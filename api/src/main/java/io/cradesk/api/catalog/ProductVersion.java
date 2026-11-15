package io.cradesk.api.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_versions")
public class ProductVersion {

    public static final String VERSION_UNIQUE = "product_versions_product_version_unique";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "released_at")
    private LocalDate releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor vacío que JPA necesita para cargar versiones desde la base de datos.
    protected ProductVersion() {
    }

    // Crea una versión nueva de un producto con su fecha de lanzamiento opcional.
    public ProductVersion(Product product, String version, LocalDate releasedAt) {
        this.product = product;
        this.version = version;
        this.releasedAt = releasedAt;
    }

    // Fija la fecha de creación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id de la versión.
    public UUID getId() {
        return id;
    }

    // Devuelve el producto al que pertenece la versión.
    public Product getProduct() {
        return product;
    }

    // Devuelve el texto de la versión, por ejemplo 1.2.0.
    public String getVersion() {
        return version;
    }

    // Devuelve la fecha de lanzamiento de la versión.
    public LocalDate getReleasedAt() {
        return releasedAt;
    }

    // Devuelve cuándo se registró la versión.
    public Instant getCreatedAt() {
        return createdAt;
    }
}
