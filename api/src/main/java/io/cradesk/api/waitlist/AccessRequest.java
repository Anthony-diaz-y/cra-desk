package io.cradesk.api.waitlist;

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
@Table(name = "access_requests")
public class AccessRequest {

    public static final String EMAIL_UNIQUE = "access_requests_email_unique";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 120)
    private String company;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor vacío que JPA necesita para cargar solicitudes desde la base de datos.
    protected AccessRequest() {
    }

    // Crea una solicitud de acceso con el email ya normalizado, la empresa y el mensaje opcionales.
    public AccessRequest(String email, String company, String message) {
        this.email = email;
        this.company = company;
        this.message = message;
    }

    // Fija la fecha de creación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el email de quien pidió acceso.
    public String getEmail() {
        return email;
    }

    // Devuelve la empresa indicada, o null si no la dio.
    public String getCompany() {
        return company;
    }

    // Devuelve el mensaje indicado, o null si no lo dio.
    public String getMessage() {
        return message;
    }

    // Devuelve cuándo se recibió la solicitud.
    public Instant getCreatedAt() {
        return createdAt;
    }
}
