package io.cradesk.api.reporting;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.audit.AuditService;
import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.reporting.ReportingDtos.EventResponse;
import io.cradesk.api.reporting.ReportingDtos.ReviewedFinding;
import io.cradesk.api.vulns.Finding;
import io.cradesk.api.vulns.FindingNotFoundException;
import io.cradesk.api.vulns.FindingRepository;

@Service
public class ReviewService {

    private final FindingRepository findings;
    private final ReportableEventRepository events;
    private final ReportRepository reports;
    private final EventService eventService;
    private final CurrentTenant tenant;
    private final Clock clock;
    private final AuditService audit;

    // Recibe los repositorios de hallazgos, eventos y reportes, el servicio de eventos, la empresa, el reloj y la auditoría.
    public ReviewService(FindingRepository findings, ReportableEventRepository events, ReportRepository reports,
            EventService eventService, CurrentTenant tenant, Clock clock, AuditService audit) {
        this.findings = findings;
        this.events = events;
        this.reports = reports;
        this.eventService = eventService;
        this.tenant = tenant;
        this.clock = clock;
        this.audit = audit;
    }

    // Confirma el hallazgo y abre su evento reportable con los tres reportes y sus relojes.
    @Transactional
    public EventResponse confirm(UUID findingId) {
        TenantPrincipal principal = tenant.principal();
        Instant now = clock.instant();
        Finding finding = pendingFinding(findingId, principal);
        finding.confirm(now, principal.userId());
        ReportableEvent event = events.save(new ReportableEvent(principal.organizationId(), finding.getId(),
                finding.getProductVersionId(), finding.getCveId(), principal.userId(), now));
        for (ReportType type : ReportType.values()) {
            reports.save(new Report(event, type, now));
        }
        reports.flush();
        audit.record("FINDING_CONFIRMED", "finding", finding.getId().toString(),
                Map.of("cveId", finding.getCveId(), "eventId", event.getId().toString()));
        return eventService.toResponse(event);
    }

    // Descarta el hallazgo guardando quién, cuándo y el motivo.
    @Transactional
    public ReviewedFinding dismiss(UUID findingId, String reason) {
        TenantPrincipal principal = tenant.principal();
        Finding finding = pendingFinding(findingId, principal);
        finding.dismiss(clock.instant(), principal.userId(), reason.strip());
        audit.record("FINDING_DISMISSED", "finding", finding.getId().toString(),
                Map.of("cveId", finding.getCveId(), "reason", finding.getDismissReason()));
        return new ReviewedFinding(finding.getId(), finding.getStatus(), finding.getReviewedAt(),
                finding.getDismissReason());
    }

    // Busca el hallazgo de la empresa bloqueando su fila y exige que siga pendiente de revisión.
    private Finding pendingFinding(UUID findingId, TenantPrincipal principal) {
        Finding finding = findings.findLockedByIdAndOrganizationId(findingId, principal.organizationId())
                .orElseThrow(() -> new FindingNotFoundException(findingId));
        if (!finding.isPendingReview()) {
            throw new FindingNotReviewableException(findingId);
        }
        return finding;
    }
}
