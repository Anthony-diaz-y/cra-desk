package io.cradesk.api.reporting;

import java.util.Map;

import io.cradesk.api.common.ConflictException;
import io.cradesk.api.common.TooManyRequestsException;
import io.cradesk.api.common.ValidationException;

public final class ReportExceptions {

    // Constructor privado: solo agrupa las excepciones de los reportes.
    private ReportExceptions() {
    }

    public static class DailyAiLimitException extends TooManyRequestsException {

        // Crea el error de cupo diario de borradores con IA agotado.
        public DailyAiLimitException(int limit) {
            super("Daily AI draft limit reached (" + limit + ")");
        }
    }

    public static class ReportAlreadySentException extends ConflictException {

        // Crea el error de reporte que ya se marcó enviado a ENISA y no se puede cambiar.
        public ReportAlreadySentException(ReportType type) {
            super("The " + type + " report was already sent to ENISA");
        }
    }

    public static class ReportNotDraftedException extends ConflictException {

        // Crea el error de aprobar un reporte que no tiene un borrador sin aprobar.
        public ReportNotDraftedException(ReportType type) {
            super("The " + type + " report has no draft waiting for approval");
        }
    }

    public static class ReportNotApprovedException extends ConflictException {

        // Crea el error de enviar o exportar un reporte que no está aprobado.
        public ReportNotApprovedException(ReportType type) {
            super("The " + type + " report must be approved first");
        }
    }

    public static class InvalidSentAtException extends ValidationException {

        // Crea el error de fecha de envío no válida.
        public InvalidSentAtException(String message) {
            super("Invalid sent date", Map.of("sentAt", message));
        }
    }
}
