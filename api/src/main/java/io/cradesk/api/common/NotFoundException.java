package io.cradesk.api.common;

public abstract class NotFoundException extends RuntimeException {

    // Crea un error de recurso inexistente con su mensaje.
    protected NotFoundException(String message) {
        super(message);
    }
}
