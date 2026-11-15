package io.cradesk.api.common;

import java.util.regex.Pattern;

public final class Names {

    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

    // Constructor privado para que esta clase de utilidades no se instancie.
    private Names() {
    }

    // Quita los espacios de los extremos y reduce cualquier grupo de espacios, también Unicode, a uno solo.
    public static String normalizeName(String raw) {
        return raw == null ? null : SPACES.matcher(raw).replaceAll(" ").strip();
    }

    // Convierte un texto vacío o solo con espacios en null y recorta los demás.
    public static String blankToNull(String raw) {
        return raw == null || normalizeName(raw).isEmpty() ? null : raw.strip();
    }
}
