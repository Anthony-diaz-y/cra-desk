package io.cradesk.api.catalog;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Indica si ya existe un producto con ese nombre, sin distinguir mayúsculas.
    @Query("select count(p) > 0 from Product p where lower(p.name) = lower(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);
}
