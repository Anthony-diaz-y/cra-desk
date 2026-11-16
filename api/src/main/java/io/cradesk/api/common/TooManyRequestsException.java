package io.cradesk.api.common;

public abstract class TooManyRequestsException extends RuntimeException {

    // Crea un error de límite de uso alcanzado con su mensaje.
    protected TooManyRequestsException(String message) {
        super(message);
    }
}
