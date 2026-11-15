package io.cradesk.api.identity;

import java.time.Instant;
import java.util.UUID;

public record InvitationCreatedResponse(UUID id, String email, Instant expiresAt, String code) {
}
