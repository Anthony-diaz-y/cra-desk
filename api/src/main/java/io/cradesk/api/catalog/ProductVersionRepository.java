package io.cradesk.api.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {

    // Busca las versiones de un producto de la más reciente a la más antigua.
    List<ProductVersion> findByProductIdOrderByCreatedAtDescIdDesc(UUID productId);

    // Busca una versión solo si pertenece a ese producto.
    Optional<ProductVersion> findByIdAndProductId(UUID id, UUID productId);

    // Indica si el producto ya tiene esa versión.
    boolean existsByProductIdAndVersion(UUID productId, String version);

    // Busca una versión de un producto por su texto exacto.
    Optional<ProductVersion> findByProductIdAndVersion(UUID productId, String version);

    // Indica si el producto tiene al menos una versión.
    boolean existsByProductId(UUID productId);
}
