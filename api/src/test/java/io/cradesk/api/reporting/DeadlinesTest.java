package io.cradesk.api.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class DeadlinesTest {

    static final Instant START = Instant.parse("2026-11-15T10:00:00Z");
    static final Instant DUE = START.plus(Duration.ofHours(24));

    // Comprueba que los plazos de cada reporte son los del CRA: 24 horas, 72 horas y 14 días.
    @Test
    void windowsMatchTheCra() {
        assertThat(ReportType.EARLY_WARNING.window()).isEqualTo(Duration.ofHours(24));
        assertThat(ReportType.NOTIFICATION.window()).isEqualTo(Duration.ofHours(72));
        assertThat(ReportType.FINAL_REPORT.window()).isEqualTo(Duration.ofDays(14));
        assertThat(ReportType.NOTIFICATION.dueAt(START)).isEqualTo(START.plus(Duration.ofHours(72)));
    }

    // Comprueba que con más de un 25 % del plazo por delante el estado es pendiente.
    @Test
    void pendingBeforeThreeQuarters() {
        Instant now = START.plus(Duration.ofHours(17).plusMinutes(59));

        assertThat(Deadlines.status(START, DUE, null, now)).isEqualTo(DeadlineStatus.PENDING);
    }

    // Comprueba que con exactamente un 25 % del plazo restante el estado es por vencer.
    @Test
    void dueSoonAtExactlyTwentyFivePercentLeft() {
        Instant now = START.plus(Duration.ofHours(18));

        assertThat(Deadlines.status(START, DUE, null, now)).isEqualTo(DeadlineStatus.DUE_SOON);
    }

    // Comprueba que un segundo después del vencimiento sin envío el estado es vencido.
    @Test
    void overdueOneSecondAfterDue() {
        assertThat(Deadlines.status(START, DUE, null, DUE.plusSeconds(1))).isEqualTo(DeadlineStatus.OVERDUE);
    }

    // Comprueba que en el instante exacto del vencimiento todavía no está vencido.
    @Test
    void notOverdueAtExactlyDue() {
        assertThat(Deadlines.status(START, DUE, null, DUE)).isEqualTo(DeadlineStatus.DUE_SOON);
    }

    // Comprueba que enviado justo en el vencimiento cuenta como a tiempo.
    @Test
    void sentOnTimeAtDue() {
        assertThat(Deadlines.status(START, DUE, DUE, DUE.plusSeconds(3600))).isEqualTo(DeadlineStatus.SENT_ON_TIME);
    }

    // Comprueba que enviado un segundo después del vencimiento cuenta como tarde.
    @Test
    void sentLateOneSecondAfter() {
        assertThat(Deadlines.status(START, DUE, DUE.plusSeconds(1), DUE.plusSeconds(2)))
                .isEqualTo(DeadlineStatus.SENT_LATE);
    }

    // Comprueba que una vez enviado, el estado no depende de la hora actual.
    @Test
    void sentStatusIgnoresNow() {
        Instant sent = START.plus(Duration.ofHours(1));

        assertThat(Deadlines.status(START, DUE, sent, START.plus(Duration.ofDays(30))))
                .isEqualTo(DeadlineStatus.SENT_ON_TIME);
    }
}
