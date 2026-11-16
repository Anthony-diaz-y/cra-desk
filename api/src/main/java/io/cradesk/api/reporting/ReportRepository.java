package io.cradesk.api.reporting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    // Busca el reporte de ese tipo de un evento y bloquea su fila hasta el final de la transacción.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.event.id = :eventId and r.type = :type")
    Optional<Report> findLocked(@Param("eventId") UUID eventId, @Param("type") ReportType type);

    // Busca el reporte de ese tipo de un evento.
    Optional<Report> findByEventIdAndType(UUID eventId, ReportType type);

    // Lista los reportes de un evento en el orden de sus plazos.
    List<Report> findByEventIdOrderByDueAtAsc(UUID eventId);
}
