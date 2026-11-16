package io.cradesk.api.reporting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.cradesk.api.vulns.FindingStatus;

public final class ReportingDtos {

    // Constructor privado: esta clase solo agrupa los records de entrada y salida del módulo.
    private ReportingDtos() {
    }

    public record DismissRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    public record ReviewedFinding(UUID id, FindingStatus status, Instant reviewedAt, String dismissReason) {
    }

    public record ReportSummary(ReportType type, Instant dueAt, DeadlineStatus status, DraftStatus draftStatus,
            Instant sentAt, String enisaReference) {
    }

    public record EventResponse(UUID id, UUID findingId, UUID productId, String productName, UUID versionId,
            String version, String cveId, Instant confirmedAt, List<ReportSummary> reports) {
    }
}
