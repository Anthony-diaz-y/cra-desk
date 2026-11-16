package io.cradesk.api.sbom;

import java.util.Map;

import io.cradesk.api.common.ValidationException;

public class InvalidUploadTargetException extends ValidationException {

    // Crea el error de parámetros de producto o versión no válidos en la subida por nombre.
    public InvalidUploadTargetException(Map<String, String> errors) {
        super("Invalid upload target", errors);
    }
}
