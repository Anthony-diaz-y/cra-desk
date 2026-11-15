package io.cradesk.api.waitlist;

import java.time.Instant;

public record AccessRequestResponse(String email, String company, String message, Instant createdAt) {
}
