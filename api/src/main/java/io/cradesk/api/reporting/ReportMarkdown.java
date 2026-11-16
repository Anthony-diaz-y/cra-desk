package io.cradesk.api.reporting;

import java.util.Map;

import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.reporting.draft.DraftContent;

final class ReportMarkdown {

    private static final Map<ReportType, String> TITLES = Map.of(
            ReportType.EARLY_WARNING, "Early warning",
            ReportType.NOTIFICATION, "Vulnerability notification",
            ReportType.FINAL_REPORT, "Final report");

    // Constructor privado para que esta clase de utilidades no se instancie.
    private ReportMarkdown() {
    }

    // Arma el reporte en Markdown con sus datos de cabecera y una sección por campo.
    static String render(ReportType type, ProductVersion version, ReportableEvent event, Report report,
            DraftContent content) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(TITLES.get(type)).append(" — ").append(event.getCveId()).append("\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| Product | ").append(version.getProduct().getName()).append(' ').append(version.getVersion())
                .append(" |\n");
        md.append("| Confirmed | ").append(event.getConfirmedAt()).append(" |\n");
        md.append("| Due | ").append(report.getDueAt()).append(" |\n");
        if (report.getSentAt() != null) {
            md.append("| Sent to ENISA | ").append(report.getSentAt()).append(" |\n");
        }
        if (report.getEnisaReference() != null) {
            md.append("| ENISA reference | ").append(report.getEnisaReference()).append(" |\n");
        }
        content.fields().forEach((field, value) -> md.append("\n## ").append(heading(field)).append("\n\n")
                .append(value).append('\n'));
        md.append("\n_Drafted with CRA Desk, a support tool; not legal advice._\n");
        return md.toString();
    }

    // Convierte un nombre de campo en camelCase en un título legible.
    static String heading(String field) {
        String spaced = field.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
