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

/**
 * One released version of a product (e.g. firmware 1.2.0). Each version will
 * later carry its own SBOM. Many versions belong to one product.
 */
@Entity
@Table(name = "product_versions")
public class ProductVersion {

    /** UNIQUE (product_id, version) from V1__catalog.sql; PostgreSQL generated this name. */
    public static final String VERSION_UNIQUE = "product_versions_product_id_version_key";

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

    protected ProductVersion() {
    }

    public ProductVersion(Product product, String version, LocalDate releasedAt) {
        this.product = product;
        this.version = version;
        this.releasedAt = releasedAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getVersion() {
        return version;
    }

    public LocalDate getReleasedAt() {
        return releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
