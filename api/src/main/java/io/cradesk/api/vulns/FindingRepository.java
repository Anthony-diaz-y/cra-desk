package io.cradesk.api.vulns;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    // Busca un hallazgo de esa empresa y bloquea su fila para revisarlo sin carreras.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Finding f where f.id = :id and f.organizationId = :organizationId")
    Optional<Finding> findLockedByIdAndOrganizationId(@Param("id") UUID id,
            @Param("organizationId") UUID organizationId);

    // Lista los hallazgos de una versión.
    List<Finding> findByProductVersionId(UUID productVersionId);

    // Lista los hallazgos de una empresa con ese estado, del más reciente al más antiguo.
    List<Finding> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, FindingStatus status);
}
