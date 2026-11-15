package io.cradesk.api.catalog;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
                UUID id,
                String name,
                String description,
                ProductClass productClass,
                Instant createdAt,
                Instant updatedAt) {
}
