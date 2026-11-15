package io.cradesk.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {

    List<ProductVersion> findByProductIdOrderByCreatedAtDesc(UUID productId);

    boolean existsByProductIdAndVersion(UUID productId, String version);

    boolean existsByProductId(UUID productId);
}
