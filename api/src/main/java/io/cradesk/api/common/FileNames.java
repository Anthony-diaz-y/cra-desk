package io.cradesk.api.common;

import java.util.Locale;

public final class FileNames {

    // Constructor privado para que esta clase de utilidades no se instancie.
    private FileNames() {
    }

    // Deja en el nombre solo letras minúsculas, números, punto, guion y guion bajo; si queda vacío usa el respaldo.
    public static String safe(String raw, String fallback) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|-+$", "");
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
