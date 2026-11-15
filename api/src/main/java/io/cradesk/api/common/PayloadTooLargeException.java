package io.cradesk.api.common;

public abstract class PayloadTooLargeException extends RuntimeException {

    // Crea un error de cuerpo demasiado grande con su mensaje.
    protected PayloadTooLargeException(String message) {
        super(message);
    }
}
