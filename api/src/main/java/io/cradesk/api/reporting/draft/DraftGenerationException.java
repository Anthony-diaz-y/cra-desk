package io.cradesk.api.reporting.draft;

public class DraftGenerationException extends RuntimeException {

    // Crea el error de un proveedor de IA que no pudo generar un borrador, con un motivo corto.
    public DraftGenerationException(String message) {
        super(message);
    }

    // Crea el error de un proveedor de IA guardando la causa original para el log.
    public DraftGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
