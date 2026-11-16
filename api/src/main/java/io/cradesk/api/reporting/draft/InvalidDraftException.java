package io.cradesk.api.reporting.draft;

import java.util.Map;

import io.cradesk.api.common.ValidationException;

public class InvalidDraftException extends ValidationException {

    // Crea el error de contenido de reporte que no tiene los campos esperados.
    public InvalidDraftException(Map<String, String> errors) {
        super("Invalid report content", errors);
    }
}
