package io.cradesk.api.reporting.draft;

import io.cradesk.api.reporting.ReportType;

public interface DraftGenerator {

    // Devuelve el nombre del generador que se guarda junto al borrador, por ejemplo TEMPLATE o CLAUDE.
    String name();

    // Genera el borrador de ese tipo de reporte con los datos del evento.
    DraftContent generate(ReportType type, DraftContext context);
}
