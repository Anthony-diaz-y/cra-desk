package io.cradesk.api.identity;

import java.util.UUID;

public record MeResponse(UUID userId, String email, UUID organizationId, String organizationName, AccessRole role) {
}
