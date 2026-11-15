package io.cradesk.api.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VersionResponse(
        UUID id,
        UUID productId,
        String version,
        LocalDate releasedAt,
        Instant createdAt) {
}
