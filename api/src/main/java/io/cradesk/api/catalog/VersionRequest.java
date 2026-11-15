package io.cradesk.api.catalog;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VersionRequest(
        @NotBlank @Size(max = 64) String version,
        LocalDate releasedAt) {
}
