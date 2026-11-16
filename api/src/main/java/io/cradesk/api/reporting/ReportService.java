package io.cradesk.api.reporting;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.audit.AuditService;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.common.FileNames;
import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.reporting.ReportExceptions.DailyAiLimitException;
import io.cradesk.api.reporting.ReportExceptions.InvalidSentAtException;
import io.cradesk.api.reporting.ReportExceptions.ReportAlreadySentException;
import io.cradesk.api.reporting.ReportExceptions.ReportNotApprovedException;
import io.cradesk.api.reporting.ReportExceptions.ReportNotDraftedException;
import io.cradesk.api.reporting.draft.DraftContent;
import io.cradesk.api.reporting.draft.DraftContext;
import io.cradesk.api.reporting.draft.DraftGenerationException;
import io.cradesk.api.reporting.draft.DraftGenerator;
import io.cradesk.api.reporting.draft.DraftGenerators;
import io.cradesk.api.vulns.Finding;
import io.cradesk.api.vulns.FindingRepository;
import io.cradesk.api.vulns.KevEntry;
import io.cradesk.api.vulns.KevEntryRepository;
import io.cradesk.api.vulns.VulnerabilityMatchRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReportService {

    static final int MAX_AI_ERROR = 300;

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final EventService events;
    private final ReportRepository reports;
    private final FindingRepository findings;
    private final KevEntryRepository kev;
    private final VulnerabilityMatchRepository matches;
    private final ProductVersionRepository versions;
    private final OrganizationRepository organizations;
    private final DraftGenerators generators;
    private final AiQuota quota;
    private final CurrentTenant tenant;
    private final TransactionTemplate transactions;
    private final JsonMapper json;
    private final Clock clock;
    private final AuditService audit;

    // Recibe los servicios y repositorios del evento, los generadores, el cupo, la empresa, las transacciones, el reloj y la auditoría.
    public ReportService(EventService events, ReportRepository reports, FindingRepository findings,
            KevEntryRepository kev, VulnerabilityMatchRepository matches, ProductVersionRepository versions,
            OrganizationRepository organizations, DraftGenerators generators, AiQuota quota, CurrentTenant tenant,
            TransactionTemplate transactions, JsonMapper json, Clock clock, AuditService audit) {
        this.events = events;
        this.reports = reports;
        this.findings = findings;
        this.kev = kev;
        this.matches = matches;
        this.versions = versions;
        this.organizations = organizations;
        this.generators = generators;
        this.quota = quota;
        this.tenant = tenant;
        this.transactions = transactions;
        this.json = json;
        this.clock = clock;
        this.audit = audit;
    }

    // Devuelve un reporte del evento de la empresa actual.
    @Transactional(readOnly = true)
    public ReportResponse get(UUID eventId, ReportType type) {
        ReportableEvent event = events.find(eventId);
        return toResponse(reports.findByEventIdAndType(event.getId(), type).orElseThrow());
    }

    // Genera el borrador con IA (si se pide y hay cupo) o con la plantilla; la IA se llama fuera de toda transacción.
    public ReportResponse draft(UUID eventId, ReportType type, boolean useAi) {
        TenantPrincipal principal = tenant.principal();
        DraftContext context = transactions.execute(status -> {
            ReportableEvent event = events.find(eventId);
            requireNotSent(reports.findByEventIdAndType(eventId, type).orElseThrow());
            return contextOf(event);
        });
        DraftGenerator generator = generators.template();
        DraftContent content = null;
        String aiError = null;
        if (useAi && generators.ai().isPresent()) {
            if (!quota.tryConsume(principal.organizationId(), generators.dailyLimit())) {
                throw new DailyAiLimitException(generators.dailyLimit());
            }
            try {
                content = generators.ai().get().generate(type, context);
                generator = generators.ai().get();
            } catch (DraftGenerationException e) {
                log.warn("AI draft failed for event {} {}: {}", eventId, type, e.getMessage());
                aiError = cut(e.getMessage());
            }
        }
        if (content == null) {
            content = generators.template().generate(type, context);
        }
        DraftContent finalContent = content;
        String finalError = aiError;
        String generatedBy = generator.name();
        return transactions.execute(status -> {
            Report report = locked(eventId, type);
            requireNotSent(report);
            report.draft(finalContent.toJson(json), generatedBy, finalError, clock.instant());
            Map<String, Object> details = details(report);
            details.put("generatedBy", generatedBy);
            details.put("aiError", finalError);
            audit.record("REPORT_DRAFTED", "report", report.getId().toString(), details);
            return toResponse(report);
        });
    }

    // Reemplaza el contenido por el que editó una persona, validado contra los campos del tipo.
    @Transactional
    public ReportResponse update(UUID eventId, ReportType type, JsonNode content) {
        DraftContent parsed = DraftContent.of(type, content == null ? json.createObjectNode() : content);
        Report report = locked(eventId, type);
        requireNotSent(report);
        report.edit(parsed.toJson(json), clock.instant());
        audit.record("REPORT_EDITED", "report", report.getId().toString(), details(report));
        return toResponse(report);
    }

    // Aprueba el borrador actual del reporte.
    @Transactional
    public ReportResponse approve(UUID eventId, ReportType type) {
        Report report = locked(eventId, type);
        requireNotSent(report);
        if (report.getDraftStatus() != DraftStatus.DRAFTED) {
            throw new ReportNotDraftedException(type);
        }
        report.approve(tenant.principal().userId(), clock.instant());
        audit.record("REPORT_APPROVED", "report", report.getId().toString(), details(report));
        return toResponse(report);
    }

    // Marca el reporte aprobado como enviado a ENISA con la hora del envío y la referencia opcional.
    @Transactional
    public ReportResponse markSent(UUID eventId, ReportType type, Instant sentAt, String enisaReference) {
        Instant now = clock.instant();
        Report report = locked(eventId, type);
        requireNotSent(report);
        if (report.getDraftStatus() != DraftStatus.APPROVED) {
            throw new ReportNotApprovedException(type);
        }
        Instant sent = sentAt == null ? now : sentAt;
        if (sent.isAfter(now)) {
            throw new InvalidSentAtException("must not be in the future");
        }
        if (sent.isBefore(report.getEvent().getConfirmedAt())) {
            throw new InvalidSentAtException("must not be before the finding was confirmed");
        }
        String reference = enisaReference == null || enisaReference.isBlank() ? null : enisaReference.strip();
        report.markSent(tenant.principal().userId(), sent, reference, now);
        Map<String, Object> details = details(report);
        details.put("sentAt", report.getSentAt().toString());
        details.put("enisaReference", reference);
        audit.record("REPORT_SENT", "report", report.getId().toString(), details);
        return toResponse(report);
    }

    // Arma la exportación en JSON o Markdown de un reporte aprobado, con un nombre de archivo limpio.
    @Transactional(readOnly = true)
    public ReportExport export(UUID eventId, ReportType type, String format) {
        ReportableEvent event = events.find(eventId);
        Report report = reports.findByEventIdAndType(event.getId(), type).orElseThrow();
        if (report.getDraftStatus() != DraftStatus.APPROVED) {
            throw new ReportNotApprovedException(type);
        }
        ProductVersion version = versions.findById(event.getProductVersionId()).orElseThrow();
        DraftContent content = DraftContent.parse(type, report.getContent(), json);
        String base = FileNames.safe(version.getProduct().getName() + "-" + version.getVersion() + "-"
                + event.getCveId() + "-" + type.name(), "report");
        if ("markdown".equalsIgnoreCase(format) || "md".equalsIgnoreCase(format)) {
            return new ReportExport(base + ".md", "text/markdown",
                    ReportMarkdown.render(type, version, event, report, content));
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", type.name());
        document.put("product", version.getProduct().getName());
        document.put("version", version.getVersion());
        document.put("cveId", event.getCveId());
        document.put("confirmedAt", event.getConfirmedAt().toString());
        document.put("dueAt", report.getDueAt().toString());
        document.put("sentAt", report.getSentAt() == null ? null : report.getSentAt().toString());
        document.put("enisaReference", report.getEnisaReference());
        document.put("fields", content.fields());
        return new ReportExport(base + ".json", "application/json", json.writeValueAsString(document));
    }

    // Reúne los datos del evento que necesita un generador de borradores.
    private DraftContext contextOf(ReportableEvent event) {
        ProductVersion version = versions.findById(event.getProductVersionId()).orElseThrow();
        Finding finding = findings.findById(event.getFindingId()).orElseThrow();
        String vulnerabilityId = finding.getVulnerability().getId();
        KevEntry entry = kev.findById(event.getCveId()).orElse(null);
        List<String> components = matches.findByProductVersionId(version.getId()).stream()
                .filter(m -> m.getVulnerability().getId().equals(vulnerabilityId))
                .map(m -> m.getComponent().getName() + " " + m.getComponent().getVersion())
                .distinct().toList();
        String organization = organizations.findById(event.getOrganizationId()).orElseThrow().getName();
        return new DraftContext(organization, version.getProduct().getName(),
                version.getProduct().getProductClass().name(), version.getVersion(), event.getCveId(),
                vulnerabilityId, finding.getVulnerability().getSummary(), finding.getVulnerability().getSeverity(),
                entry == null ? null : entry.getDateAdded(), entry == null ? null : entry.getVendorProject(),
                entry == null ? null : entry.getProduct(), components.isEmpty() ? List.of("unknown") : components,
                event.getConfirmedAt());
    }

    // Busca el reporte del evento de la empresa actual bloqueando su fila.
    private Report locked(UUID eventId, ReportType type) {
        ReportableEvent event = events.find(eventId);
        return reports.findLocked(event.getId(), type).orElseThrow();
    }

    // Exige que el reporte todavía no esté enviado a ENISA.
    private static void requireNotSent(Report report) {
        if (report.isSent()) {
            throw new ReportAlreadySentException(report.getType());
        }
    }

    // Convierte un reporte en su respuesta, con el estado del reloj calculado ahora.
    ReportResponse toResponse(Report r) {
        JsonNode content = r.getContent() == null ? null : json.readTree(r.getContent());
        return new ReportResponse(r.getType(), r.getDueAt(), r.statusAt(clock.instant()), r.getDraftStatus(),
                content, r.getGeneratedBy(), r.getAiError(), r.getApprovedAt(), r.getSentAt(), r.getEnisaReference());
    }

    // Arma los detalles de auditoría comunes de un reporte: evento, tipo y CVE.
    private static Map<String, Object> details(Report report) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", report.getEvent().getId().toString());
        details.put("type", report.getType().name());
        details.put("cveId", report.getEvent().getCveId());
        return details;
    }

    // Recorta el motivo del fallo de la IA al largo de su columna.
    private static String cut(String message) {
        String text = message == null ? "AI provider: unknown error" : message;
        return text.length() > MAX_AI_ERROR ? text.substring(0, MAX_AI_ERROR) : text;
    }

    public record ReportResponse(ReportType type, Instant dueAt, DeadlineStatus status, DraftStatus draftStatus,
            JsonNode content, String generatedBy, String aiError, Instant approvedAt, Instant sentAt,
            String enisaReference) {
    }

    public record ReportExport(String filename, String contentType, String body) {
    }
}
