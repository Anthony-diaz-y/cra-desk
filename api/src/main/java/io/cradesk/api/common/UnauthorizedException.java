package io.cradesk.api.common;

public abstract class UnauthorizedException extends RuntimeException {

    // Crea un error de credenciales no válidas con su mensaje.
    protected UnauthorizedException(String message) {
        super(message);
    }
}
