package io.cradesk.api.sbom;

import java.util.Map;

import io.cradesk.api.common.ValidationException;

public class InvalidSbomException extends ValidationException {

    // Crea el error de SBOM no válido con los errores encontrados por ruta.
    public InvalidSbomException(Map<String, String> errors) {
        super("Not a valid CycloneDX JSON SBOM", errors);
    }
}
