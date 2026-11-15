package io.cradesk.api.vulns;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    // Lista los hallazgos de una versión.
    List<Finding> findByProductVersionId(UUID productVersionId);

    // Lista los hallazgos de una empresa con ese estado, del más reciente al más antiguo.
    List<Finding> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, FindingStatus status);
}
