package io.cradesk.api.catalog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    public static final String NAME_UNIQUE = "products_name_unique";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_class", nullable = false)
    private ProductClass productClass;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructor vacío que JPA necesita para cargar productos desde la base de datos.
    protected Product() {
    }

    // Crea un producto nuevo con su nombre, descripción y clase del CRA.
    public Product(String name, String description, ProductClass productClass) {
        this.name = name;
        this.description = description;
        this.productClass = productClass;
    }

    // Reemplaza el nombre, la descripción y la clase del producto.
    void update(String name, String description, ProductClass productClass) {
        this.name = name;
        this.description = description;
        this.productClass = productClass;
    }

    // Fija la fecha de creación y de modificación antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    // Actualiza la fecha de modificación antes de guardar cambios.
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id del producto.
    public UUID getId() {
        return id;
    }

    // Devuelve el nombre del producto.
    public String getName() {
        return name;
    }

    // Devuelve la descripción del producto.
    public String getDescription() {
        return description;
    }

    // Devuelve la clase del producto según el CRA.
    public ProductClass getProductClass() {
        return productClass;
    }

    // Devuelve cuándo se creó el producto.
    public Instant getCreatedAt() {
        return createdAt;
    }

    // Devuelve cuándo se modificó el producto por última vez.
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
