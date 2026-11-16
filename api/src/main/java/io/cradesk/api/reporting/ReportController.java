package io.cradesk.api.reporting;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.cradesk.api.reporting.ReportService.ReportExport;
import io.cradesk.api.reporting.ReportService.ReportResponse;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/events/{eventId}/reports/{type}")
public class ReportController {

    private final ReportService reports;

    // Recibe el servicio de reportes por inyección de dependencias.
    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    // Devuelve el reporte de ese tipo del evento.
    @GetMapping
    public ReportResponse get(@PathVariable UUID eventId, @PathVariable ReportType type) {
        return reports.get(eventId, type);
    }

    // Genera el borrador del reporte con IA (ai=true, por defecto) o con la plantilla (ai=false).
    @PostMapping("/draft")
    public ReportResponse draft(@PathVariable UUID eventId, @PathVariable ReportType type,
            @RequestParam(defaultValue = "true") boolean ai) {
        return reports.draft(eventId, type, ai);
    }

    // Reemplaza el contenido del borrador por el que editó la persona.
    @PutMapping
    public ReportResponse update(@PathVariable UUID eventId, @PathVariable ReportType type,
            @RequestBody UpdateReportRequest request) {
        return reports.update(eventId, type, request.content());
    }

    // Aprueba el borrador del reporte.
    @PostMapping("/approve")
    public ReportResponse approve(@PathVariable UUID eventId, @PathVariable ReportType type) {
        return reports.approve(eventId, type);
    }

    // Marca el reporte como enviado a ENISA, lo que detiene su reloj.
    @PostMapping("/sent")
    public ReportResponse sent(@PathVariable UUID eventId, @PathVariable ReportType type,
            @Valid @RequestBody(required = false) MarkSentRequest request) {
        MarkSentRequest body = request == null ? new MarkSentRequest(null, null) : request;
        return reports.markSent(eventId, type, body.sentAt(), body.enisaReference());
    }

    // Descarga el reporte aprobado en JSON o en Markdown.
    @GetMapping("/export")
    public ResponseEntity<String> export(@PathVariable UUID eventId, @PathVariable ReportType type,
            @RequestParam(defaultValue = "json") String format) {
        ReportExport export = reports.export(eventId, type, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType() + ";charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .body(export.body());
    }

    public record UpdateReportRequest(JsonNode content) {
    }

    public record MarkSentRequest(Instant sentAt, @Size(max = 100) String enisaReference) {
    }
}
