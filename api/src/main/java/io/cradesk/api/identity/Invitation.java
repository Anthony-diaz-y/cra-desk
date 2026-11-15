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
@Table(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor vacío que JPA necesita para cargar invitaciones desde la base de datos.
    protected Invitation() {
    }

    // Crea una invitación para un email con el hash de su código y su fecha de vencimiento.
    public Invitation(String email, String codeHash, Instant expiresAt) {
        this.email = Emails.normalize(email);
        this.codeHash = codeHash;
        this.expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    // Indica si la invitación sirve para ese email en ese instante: no usada, no vencida y del mismo email.
    public boolean isUsableBy(String normalizedEmail, Instant now) {
        return acceptedAt == null && now.isBefore(expiresAt) && email.equals(normalizedEmail);
    }

    // Marca la invitación como usada para que no sirva otra vez.
    public void markAccepted(Instant now) {
        acceptedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    // Fija la fecha de creación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id de la invitación.
    public UUID getId() {
        return id;
    }

    // Devuelve el email invitado.
    public String getEmail() {
        return email;
    }

    // Devuelve cuándo vence la invitación.
    public Instant getExpiresAt() {
        return expiresAt;
    }

    // Devuelve cuándo se aceptó la invitación, o null si no se ha usado.
    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
