package io.cradesk.api.common;

public abstract class BadRequestException extends RuntimeException {

    // Crea un error de petición no válida con su mensaje.
    protected BadRequestException(String message) {
        super(message);
    }
}
