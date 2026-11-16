package io.cradesk.api.reporting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportableEventRepository extends JpaRepository<ReportableEvent, UUID> {

    // Busca un evento solo si pertenece a esa empresa.
    Optional<ReportableEvent> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // Lista los eventos de una empresa del más reciente al más antiguo.
    List<ReportableEvent> findByOrganizationIdOrderByConfirmedAtDesc(UUID organizationId);
}
