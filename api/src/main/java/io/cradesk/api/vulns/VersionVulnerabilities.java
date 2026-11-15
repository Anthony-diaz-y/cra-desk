package io.cradesk.api.vulns;

import java.time.Instant;
import java.util.List;

public record VersionVulnerabilities(ScanStatus status, Instant finishedAt, String error, Integer componentsScanned,
        Integer componentsNotScanned, List<VulnerabilityResponse> items) {
}
