package io.cradesk.api.vulns;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FindingResponse(UUID id, UUID productId, String productName, UUID versionId, String version,
        String vulnerabilityId, String cveId, String summary, String severity, LocalDate kevDateAdded,
        boolean present, FindingStatus status, Instant createdAt) {
}
