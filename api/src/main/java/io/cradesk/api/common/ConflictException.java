package io.cradesk.api.common;

public abstract class ConflictException extends RuntimeException {

    // Crea un error de conflicto con el estado actual con su mensaje.
    protected ConflictException(String message) {
        super(message);
    }
}
