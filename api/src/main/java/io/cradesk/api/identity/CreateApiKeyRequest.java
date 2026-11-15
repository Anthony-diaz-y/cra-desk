package io.cradesk.api.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(@NotBlank @Size(max = 80) String name) {
}
