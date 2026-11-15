package io.cradesk.api.sbom;

import java.util.Map;

import io.cradesk.api.common.ValidationException;

public class InvalidPageException extends ValidationException {

    // Crea el error de parámetros de paginación fuera de rango.
    public InvalidPageException(Map<String, String> errors) {
        super("Invalid page parameters", errors);
    }
}
