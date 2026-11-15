package io.cradesk.api.identity;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {

    public static final String EMAIL_UNIQUE = "users_email_unique";

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessRole role;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor vacío que JPA necesita para cargar usuarios desde la base de datos.
    protected UserAccount() {
    }

    // Crea un usuario de una empresa con el email recortado y en minúsculas.
    public UserAccount(Organization organization, String email, String passwordHash, AccessRole role) {
        this.organization = organization;
        this.email = Emails.normalize(email);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // Indica si la cuenta está bloqueada en ese instante por demasiados fallos de login.
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    // Suma un fallo de login y bloquea la cuenta 15 minutos al llegar a cinco fallos.
    public void recordFailedLogin(Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION).truncatedTo(ChronoUnit.MICROS);
            failedLoginAttempts = 0;
        }
    }

    // Reinicia el contador de fallos y quita el bloqueo tras un login correcto.
    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    // Fija la fecha de creación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id del usuario.
    public UUID getId() {
        return id;
    }

    // Devuelve la empresa a la que pertenece el usuario.
    public Organization getOrganization() {
        return organization;
    }

    // Devuelve el email normalizado del usuario.
    public String getEmail() {
        return email;
    }

    // Devuelve el hash de la contraseña, o null si el usuario no tiene contraseña.
    public String getPasswordHash() {
        return passwordHash;
    }

    // Devuelve el rol del usuario.
    public AccessRole getRole() {
        return role;
    }

    // Devuelve cuántos fallos de login seguidos lleva el usuario.
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    // Devuelve hasta cuándo está bloqueada la cuenta, o null si no lo está.
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    // Devuelve cuándo se creó el usuario.
    public Instant getCreatedAt() {
        return createdAt;
    }
}
