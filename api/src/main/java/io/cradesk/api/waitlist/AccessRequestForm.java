package io.cradesk.api.waitlist;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccessRequestForm(
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 120) String company,
        @Size(max = 1000) String message) {

    // Quita los espacios alrededor del email antes de validarlo.
    public AccessRequestForm {
        email = email == null ? null : email.strip();
    }
}
