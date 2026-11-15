package io.cradesk.api.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(@NotBlank @Size(max = 254) String email) {
}
