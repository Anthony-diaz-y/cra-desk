package io.cradesk.api.sbom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SbomRepository extends JpaRepository<Sbom, UUID> {

    // Devuelve los ids de todas las versiones de producto que tienen SBOM.
    @Query("select s.productVersionId from Sbom s")
    List<UUID> findAllProductVersionIds();

    // Busca el SBOM de una versión y lo bloquea en modo compartido para que no se reemplace mientras se usa.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from Sbom s where s.productVersionId = :versionId")
    Optional<Sbom> findLockedByProductVersionId(@Param("versionId") UUID productVersionId);

    // Busca el SBOM de una versión de producto.
    Optional<Sbom> findByProductVersionId(UUID productVersionId);
}
