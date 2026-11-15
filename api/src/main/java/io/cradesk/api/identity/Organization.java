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
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor vacío que JPA necesita para cargar empresas desde la base de datos.
    protected Organization() {
    }

    // Crea una empresa nueva con su nombre.
    public Organization(String name) {
        this.name = name;
    }

    // Fija la fecha de creación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id de la empresa.
    public UUID getId() {
        return id;
    }

    // Devuelve el nombre de la empresa.
    public String getName() {
        return name;
    }

    // Devuelve cuándo se creó la empresa.
    public Instant getCreatedAt() {
        return createdAt;
    }
}
