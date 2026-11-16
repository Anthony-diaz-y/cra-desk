package io.cradesk.api.reporting;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.reporting.ReportingDtos.EventResponse;
import io.cradesk.api.reporting.ReportingDtos.ReportSummary;

@Service
public class EventService {

    private final ReportableEventRepository events;
    private final ReportRepository reports;
    private final ProductVersionRepository versions;
    private final CurrentTenant tenant;
    private final Clock clock;

    // Recibe los repositorios, la empresa actual y el reloj.
    public EventService(ReportableEventRepository events, ReportRepository reports, ProductVersionRepository versions,
            CurrentTenant tenant, Clock clock) {
        this.events = events;
        this.reports = reports;
        this.versions = versions;
        this.tenant = tenant;
        this.clock = clock;
    }

    // Lista los eventos reportables de la empresa actual con el estado de sus relojes.
    @Transactional(readOnly = true)
    public List<EventResponse> list() {
        return events.findByOrganizationIdOrderByConfirmedAtDesc(tenant.organizationId()).stream()
                .map(this::toResponse).toList();
    }

    // Devuelve un evento de la empresa actual o lanza 404.
    @Transactional(readOnly = true)
    public EventResponse get(UUID id) {
        return toResponse(find(id));
    }

    // Busca un evento de la empresa actual o lanza 404.
    ReportableEvent find(UUID id) {
        return events.findByIdAndOrganizationId(id, tenant.organizationId())
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    // Convierte un evento y sus reportes en la respuesta con los estados calculados ahora.
    EventResponse toResponse(ReportableEvent event) {
        Instant now = clock.instant();
        ProductVersion version = versions.findById(event.getProductVersionId()).orElseThrow();
        List<ReportSummary> summaries = reports.findByEventIdOrderByDueAtAsc(event.getId()).stream()
                .map(r -> new ReportSummary(r.getType(), r.getDueAt(), r.statusAt(now), r.getDraftStatus(),
                        r.getSentAt(), r.getEnisaReference()))
                .toList();
        return new EventResponse(event.getId(), event.getFindingId(), version.getProduct().getId(),
                version.getProduct().getName(), version.getId(), version.getVersion(), event.getCveId(),
                event.getConfirmedAt(), summaries);
    }
}
