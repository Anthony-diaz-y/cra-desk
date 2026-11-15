package io.cradesk.api.identity;

import java.util.Locale;

public final class Emails {

    // Constructor privado para que esta clase de utilidades no se instancie.
    private Emails() {
    }

    // Recorta los espacios y pasa el email a minúsculas para compararlo siempre igual.
    public static String normalize(String raw) {
        return raw == null ? null : raw.strip().toLowerCase(Locale.ROOT);
    }
}
