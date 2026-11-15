package io.cradesk.api.sbom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SbomRepository extends JpaRepository<Sbom, UUID> {

    // Devuelve los ids de todas las versiones de producto que tienen SBOM.
    @Query("select s.productVersionId from Sbom s")
    List<UUID> findAllProductVersionIds();

    // Busca el SBOM de una versión de producto.
    Optional<Sbom> findByProductVersionId(UUID productVersionId);
}
