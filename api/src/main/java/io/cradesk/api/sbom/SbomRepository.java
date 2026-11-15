package io.cradesk.api.sbom;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SbomRepository extends JpaRepository<Sbom, UUID> {

    // Busca el SBOM de una versión de producto.
    Optional<Sbom> findByProductVersionId(UUID productVersionId);
}
