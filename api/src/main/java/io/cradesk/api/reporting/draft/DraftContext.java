package io.cradesk.api.reporting.draft;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DraftContext(String organizationName, String productName, String productClass, String version,
        String cveId, String vulnerabilityId, String summary, String severity, LocalDate kevDateAdded,
        String kevVendorProject, String kevProduct, List<String> affectedComponents, Instant confirmedAt) {
}
