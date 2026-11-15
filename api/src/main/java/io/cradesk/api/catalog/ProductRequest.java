package io.cradesk.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
                @NotBlank @Size(max = 120) String name,
                @Size(max = 2000) String description,
                @NotNull ProductClass productClass) {
}
