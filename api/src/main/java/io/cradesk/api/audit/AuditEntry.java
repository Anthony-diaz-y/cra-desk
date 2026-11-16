package io.cradesk.api.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private long seq;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String details;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    // Constructor vacío que JPA necesita para cargar entradas desde la base de datos.
    protected AuditEntry() {
    }

    // Devuelve el texto del actor que entra en el hash: rol y id, o system.
    public String actor() {
        return actorRole + ":" + (actorId == null ? "system" : actorId);
    }

    // Recalcula el hash de la entrada a partir de su contenido guardado.
    public String recomputeHash() {
        return AuditHasher.hash(prevHash, seq, occurredAt, actor(), action, targetType, targetId, details);
    }

    // Devuelve el id de la entrada.
    public UUID getId() {
        return id;
    }

    // Devuelve la empresa dueña de la entrada.
    public UUID getOrganizationId() {
        return organizationId;
    }

    // Devuelve la posición de la entrada en la cadena de su empresa.
    public long getSeq() {
        return seq;
    }

    // Devuelve cuándo ocurrió la acción.
    public Instant getOccurredAt() {
        return occurredAt;
    }

    // Devuelve el id de quien hizo la acción, o null si fue el sistema.
    public UUID getActorId() {
        return actorId;
    }

    // Devuelve el rol de quien hizo la acción.
    public String getActorRole() {
        return actorRole;
    }

    // Devuelve el nombre de la acción.
    public String getAction() {
        return action;
    }

    // Devuelve el tipo del objeto afectado.
    public String getTargetType() {
        return targetType;
    }

    // Devuelve el id del objeto afectado.
    public String getTargetId() {
        return targetId;
    }

    // Devuelve los detalles en JSON canónico.
    public String getDetails() {
        return details;
    }

    // Devuelve el hash de la entrada anterior.
    public String getPrevHash() {
        return prevHash;
    }

    // Devuelve el hash de esta entrada.
    public String getHash() {
        return hash;
    }
}
