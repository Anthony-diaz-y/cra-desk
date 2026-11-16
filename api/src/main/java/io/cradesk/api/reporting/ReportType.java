package io.cradesk.api.reporting;

import java.time.Duration;
import java.time.Instant;

public enum ReportType {
    EARLY_WARNING(Duration.ofHours(24)),
    NOTIFICATION(Duration.ofHours(72)),
    FINAL_REPORT(Duration.ofDays(14));

    private final Duration window;

    // Guarda el plazo que el CRA da para este reporte desde la confirmación.
    ReportType(Duration window) {
        this.window = window;
    }

    // Devuelve el plazo del reporte.
    public Duration window() {
        return window;
    }

    // Calcula el vencimiento del reporte a partir del momento de la confirmación.
    public Instant dueAt(Instant confirmedAt) {
        return confirmedAt.plus(window);
    }
}
