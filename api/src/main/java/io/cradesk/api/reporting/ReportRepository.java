package io.cradesk.api.reporting;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    // Lista los reportes de un evento en el orden de sus plazos.
    List<Report> findByEventIdOrderByDueAtAsc(UUID eventId);
}
