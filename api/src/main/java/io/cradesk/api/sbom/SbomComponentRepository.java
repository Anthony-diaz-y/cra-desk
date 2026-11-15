package io.cradesk.api.sbom;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SbomComponentRepository extends JpaRepository<SbomComponent, UUID> {

    // Devuelve una página de los componentes de un SBOM.
    Page<SbomComponent> findBySbomId(UUID sbomId, Pageable pageable);
}
