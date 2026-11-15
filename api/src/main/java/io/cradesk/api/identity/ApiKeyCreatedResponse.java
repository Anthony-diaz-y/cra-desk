package io.cradesk.api.identity;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyCreatedResponse(UUID id, String name, String prefix, String key, Instant createdAt) {
}
