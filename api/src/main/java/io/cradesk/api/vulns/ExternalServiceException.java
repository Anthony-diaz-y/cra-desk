package io.cradesk.api.vulns;

public class ExternalServiceException extends RuntimeException {

    // Crea el error de un servicio externo (OSV o KEV) con un motivo corto.
    public ExternalServiceException(String message) {
        super(message);
    }

    // Crea el error de un servicio externo guardando la causa original.
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
