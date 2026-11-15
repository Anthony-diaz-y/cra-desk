package io.cradesk.api.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank @Size(max = 200) String code,
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 12) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 120) String organizationName) {
}
