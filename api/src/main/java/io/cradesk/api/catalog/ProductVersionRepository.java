package io.cradesk.api.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {

    List<ProductVersion> findByProductIdOrderByCreatedAtDescIdDesc(UUID productId);

    /** Both ids must match, so a version can't be read through another product. */
    Optional<ProductVersion> findByIdAndProductId(UUID id, UUID productId);

    boolean existsByProductIdAndVersion(UUID productId, String version);

    boolean existsByProductId(UUID productId);
}
