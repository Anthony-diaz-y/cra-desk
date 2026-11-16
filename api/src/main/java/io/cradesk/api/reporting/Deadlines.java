package io.cradesk.api.reporting;

import java.time.Duration;
import java.time.Instant;

public final class Deadlines {

    static final double DUE_SOON_FRACTION = 0.25;

    // Constructor privado para que esta clase de utilidades no se instancie.
    private Deadlines() {
    }

    // Calcula el estado de un plazo: enviado a tiempo o tarde, o sin enviar pendiente, por vencer o vencido.
    public static DeadlineStatus status(Instant start, Instant due, Instant sentAt, Instant now) {
        if (sentAt != null) {
            return sentAt.isAfter(due) ? DeadlineStatus.SENT_LATE : DeadlineStatus.SENT_ON_TIME;
        }
        if (now.isAfter(due)) {
            return DeadlineStatus.OVERDUE;
        }
        long total = Duration.between(start, due).toMillis();
        long left = Duration.between(now, due).toMillis();
        return left <= total * DUE_SOON_FRACTION ? DeadlineStatus.DUE_SOON : DeadlineStatus.PENDING;
    }
}
