package io.cradesk.api.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.admin")
public record AdminProperties(String token) {

    static final int MIN_TOKEN_LENGTH = 32;

    // Valida al arrancar que el token de admin, si está configurado, tenga al menos 32 caracteres.
    public AdminProperties {
        if (token != null && !token.isBlank() && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException(
                    "cradesk.admin.token must be at least " + MIN_TOKEN_LENGTH + " characters");
        }
    }

    // Indica si hay un token de admin configurado.
    public boolean enabled() {
        return token != null && !token.isBlank();
    }
}
