package io.cradesk.api.sbom;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SbomComponentRepository extends JpaRepository<SbomComponent, UUID> {

    // Devuelve todos los componentes de un SBOM.
    List<SbomComponent> findAllBySbomId(UUID sbomId);

    // Devuelve una página de los componentes de un SBOM.
    Page<SbomComponent> findBySbomId(UUID sbomId, Pageable pageable);
}
