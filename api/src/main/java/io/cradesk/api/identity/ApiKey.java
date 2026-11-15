package io.cradesk.api.identity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 8, updatable = false)
    private String prefix;

    @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
    private String keyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Constructor vacío que JPA necesita para cargar claves desde la base de datos.
    protected ApiKey() {
    }

    // Crea una clave de una empresa con su nombre, su prefijo visible y el hash de la clave completa.
    public ApiKey(UUID organizationId, UUID createdBy, String name, String prefix, String keyHash) {
        this.organizationId = organizationId;
        this.createdBy = createdBy;
        this.name = name;
        this.prefix = prefix;
        this.keyHash = keyHash;
    }

    // Indica si la clave sigue activa, es decir, si no fue revocada.
    public boolean isActive() {
        return revokedAt == null;
    }

    // Revoca la clave para que deje de autenticar.
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }

    // Registra el último momento en que se usó la clave.
    public void markUsed(Instant now) {
        lastUsedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    // Fija la fecha de creación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id de la clave.
    public UUID getId() {
        return id;
    }

    // Devuelve el id de la empresa dueña de la clave.
    public UUID getOrganizationId() {
        return organizationId;
    }

    // Devuelve el id del usuario que creó la clave.
    public UUID getCreatedBy() {
        return createdBy;
    }

    // Devuelve el nombre descriptivo de la clave.
    public String getName() {
        return name;
    }

    // Devuelve el prefijo visible que identifica la clave sin exponerla.
    public String getPrefix() {
        return prefix;
    }

    // Devuelve el hash SHA-256 de la clave completa.
    public String getKeyHash() {
        return keyHash;
    }

    // Devuelve cuándo se creó la clave.
    public Instant getCreatedAt() {
        return createdAt;
    }

    // Devuelve cuándo se usó la clave por última vez, o null si nunca se usó.
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    // Devuelve cuándo se revocó la clave, o null si sigue activa.
    public Instant getRevokedAt() {
        return revokedAt;
    }
}
