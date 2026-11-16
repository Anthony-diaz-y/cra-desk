package io.cradesk.api.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.sbom.InvalidPageException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    static final int MAX_PAGE_SIZE = 200;
    static final int VERIFY_BATCH = 500;
    static final String SYSTEM_ROLE = "SYSTEM";

    private final JdbcTemplate jdbc;
    private final AuditEntryRepository entries;
    private final CurrentTenant tenant;
    private final EntityManager entityManager;
    private final JsonMapper json;
    private final Clock clock;

    // Recibe el acceso JDBC, el repositorio de entradas, la empresa actual, el EntityManager, JSON y el reloj.
    public AuditService(JdbcTemplate jdbc, AuditEntryRepository entries, CurrentTenant tenant,
            EntityManager entityManager, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.entries = entries;
        this.tenant = tenant;
        this.entityManager = entityManager;
        this.json = json;
        this.clock = clock;
    }

    // Registra una acción del usuario actual en la cadena de su empresa; debe llamarse dentro de una transacción.
    public void record(String action, String targetType, String targetId, Map<String, ?> details) {
        TenantPrincipal principal = tenant.principal();
        append(principal.organizationId(), principal.userId(), principal.role().name(), action, targetType, targetId,
                details);
    }

    // Registra una acción hecha por el sistema en la cadena de esa empresa; debe llamarse dentro de una transacción.
    public void recordSystem(UUID organizationId, String action, String targetType, String targetId,
            Map<String, ?> details) {
        append(organizationId, null, SYSTEM_ROLE, action, targetType, targetId, details);
    }

    // Bloquea la cabeza de la cadena, calcula el hash de la entrada nueva y la guarda junto con la cabeza.
    private void append(UUID organizationId, UUID actorId, String actorRole, String action, String targetType,
            String targetId, Map<String, ?> details) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Audit entries must be recorded inside a transaction");
        }
        jdbc.update("INSERT INTO audit_chain_heads (organization_id, last_seq, last_hash) VALUES (?, 0, ?)"
                + " ON CONFLICT (organization_id) DO NOTHING", organizationId, AuditHasher.GENESIS);
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash FROM audit_chain_heads"
                + " WHERE organization_id = ? FOR UPDATE", organizationId);
        long seq = ((Number) head.get("last_seq")).longValue() + 1;
        String prevHash = (String) head.get("last_hash");
        Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String canonical = AuditHasher.canonical(details == null ? Map.of() : details);
        String actor = actorRole + ":" + (actorId == null ? "system" : actorId);
        String hash = AuditHasher.hash(prevHash, seq, at, actor, action, targetType, targetId, canonical);
        jdbc.update("INSERT INTO audit_entries (id, organization_id, seq, occurred_at, actor_id, actor_role, action,"
                + " target_type, target_id, details, prev_hash, hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), organizationId, seq, java.sql.Timestamp.from(at), actorId, actorRole, action,
                targetType, targetId, canonical, prevHash, hash);
        jdbc.update("UPDATE audit_chain_heads SET last_seq = ?, last_hash = ? WHERE organization_id = ?", seq, hash,
                organizationId);
    }

    // Devuelve una página de entradas de la empresa actual, la más reciente primero.
    @Transactional(readOnly = true)
    public AuditPage page(int page, int size) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (page < 0) {
            errors.put("page", "must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.put("size", "must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!errors.isEmpty()) {
            throw new InvalidPageException(errors);
        }
        Page<AuditEntry> result = entries.findByOrganizationId(tenant.organizationId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "seq")));
        List<AuditEntryResponse> items = result.map(e -> AuditEntryResponse.of(e, json)).getContent();
        return new AuditPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    // Recorre la cadena de la empresa actual sobre una sola foto de la base y comprueba orden, enlaces, hashes y la cabeza.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditVerification verify() {
        UUID organizationId = tenant.organizationId();
        long lastSeq = 0;
        String lastHash = AuditHasher.GENESIS;
        long count = 0;
        Slice<AuditEntry> slice;
        do {
            slice = entries.findByOrganizationIdAndSeqGreaterThanOrderBySeqAsc(organizationId, lastSeq,
                    PageRequest.of(0, VERIFY_BATCH));
            for (AuditEntry entry : slice) {
                long expected = lastSeq + 1;
                if (entry.getSeq() != expected || !entry.getPrevHash().equals(lastHash)) {
                    return new AuditVerification(false, count, expected);
                }
                if (!entry.recomputeHash().equals(entry.getHash())) {
                    return new AuditVerification(false, count, entry.getSeq());
                }
                lastSeq = entry.getSeq();
                lastHash = entry.getHash();
                count++;
            }
            entityManager.clear();
        } while (slice.hasNext());
        List<Map<String, Object>> head = jdbc.queryForList("SELECT last_seq, last_hash FROM audit_chain_heads"
                + " WHERE organization_id = ?", organizationId);
        long headSeq = head.isEmpty() ? 0 : ((Number) head.get(0).get("last_seq")).longValue();
        String headHash = head.isEmpty() ? AuditHasher.GENESIS : (String) head.get(0).get("last_hash");
        if (headSeq != lastSeq || !headHash.equals(lastHash)) {
            return new AuditVerification(false, count, lastSeq + 1);
        }
        return new AuditVerification(true, count, null);
    }

    public record AuditPage(List<AuditEntryResponse> items, int page, int size, long total, int totalPages) {
    }
}
