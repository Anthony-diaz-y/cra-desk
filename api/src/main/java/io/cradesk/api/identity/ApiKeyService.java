package io.cradesk.api.identity;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.audit.AuditService;

@Service
public class ApiKeyService {

    static final String KEY_START = "crd_";
    static final int PREFIX_LENGTH = 8;
    static final int PREFIX_BYTES = 6;
    static final int SECRET_BYTES = 32;

    private final ApiKeyRepository keys;
    private final CurrentTenant tenant;
    private final Clock clock;
    private final AuditService audit;

    // Recibe el repositorio de claves, el acceso al usuario actual, el reloj y la auditoría.
    public ApiKeyService(ApiKeyRepository keys, CurrentTenant tenant, Clock clock, AuditService audit) {
        this.keys = keys;
        this.tenant = tenant;
        this.clock = clock;
        this.audit = audit;
    }

    // Genera una clave nueva para la empresa actual, guarda solo su hash y devuelve la clave una única vez.
    @Transactional
    public ApiKeyCreatedResponse create(CreateApiKeyRequest request) {
        TenantPrincipal principal = tenant.principal();
        String prefix = SecretTokens.random(PREFIX_BYTES);
        String key = KEY_START + prefix + "_" + SecretTokens.random(SECRET_BYTES);
        ApiKey saved = keys.save(new ApiKey(principal.organizationId(), principal.userId(), request.name().strip(),
                prefix, SecretTokens.sha256Hex(key)));
        audit.record("API_KEY_CREATED", "api_key", saved.getId().toString(),
                Map.of("name", saved.getName(), "prefix", saved.getPrefix()));
        return new ApiKeyCreatedResponse(saved.getId(), saved.getName(), saved.getPrefix(), key,
                saved.getCreatedAt());
    }

    // Lista las claves de la empresa actual sin exponer ningún secreto.
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        return keys.findAllByOrganizationIdOrderByCreatedAtDesc(tenant.organizationId()).stream()
                .map(ApiKeyService::toResponse).toList();
    }

    // Revoca una clave de la empresa actual o lanza 404 si no existe o es de otra empresa.
    @Transactional
    public void revoke(UUID id) {
        ApiKey key = keys.findByIdAndOrganizationId(id, tenant.organizationId())
                .orElseThrow(() -> new ApiKeyNotFoundException(id));
        key.revoke(clock.instant());
        audit.record("API_KEY_REVOKED", "api_key", key.getId().toString(),
                Map.of("name", key.getName(), "prefix", key.getPrefix()));
    }

    // Valida una clave completa y devuelve el principal CLI de su empresa si está activa y coincide.
    @Transactional
    public Optional<TenantPrincipal> authenticate(String rawKey) {
        if (rawKey == null || rawKey.length() <= KEY_START.length() + PREFIX_LENGTH + 1
                || !rawKey.startsWith(KEY_START) || rawKey.charAt(KEY_START.length() + PREFIX_LENGTH) != '_') {
            return Optional.empty();
        }
        String prefix = rawKey.substring(KEY_START.length(), KEY_START.length() + PREFIX_LENGTH);
        return keys.findByPrefix(prefix)
                .filter(ApiKey::isActive)
                .filter(k -> SecretTokens.constantTimeEquals(SecretTokens.sha256Hex(rawKey), k.getKeyHash()))
                .map(k -> {
                    keys.touchLastUsed(k.getId(), clock.instant().truncatedTo(ChronoUnit.MICROS));
                    return new TenantPrincipal(k.getOrganizationId(), k.getCreatedBy(), null, AccessRole.CLI);
                });
    }

    // Convierte una clave guardada en su respuesta JSON sin secretos.
    static ApiKeyResponse toResponse(ApiKey k) {
        return new ApiKeyResponse(k.getId(), k.getName(), k.getPrefix(), k.getCreatedAt(), k.getLastUsedAt(),
                k.getRevokedAt());
    }
}
