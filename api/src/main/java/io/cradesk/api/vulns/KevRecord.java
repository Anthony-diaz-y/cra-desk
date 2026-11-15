package io.cradesk.api.vulns;

import java.time.LocalDate;

public record KevRecord(String cveId, String vendorProject, String product, String vulnerabilityName,
        LocalDate dateAdded, LocalDate dueDate, String knownRansomware, String shortDescription) {
}
