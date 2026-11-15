package io.cradesk.api.common;

import java.util.Map;

public abstract class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    // Crea un error de validación con su mensaje general y el mapa campo → mensaje.
    protected ValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Map.copyOf(errors);
    }

    // Devuelve los errores por campo o ruta.
    public Map<String, String> errors() {
        return errors;
    }
}
