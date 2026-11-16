package io.cradesk.api.reporting.draft;

import static io.cradesk.api.reporting.draft.DraftPrompt.TO_BE_COMPLETED;

import java.util.LinkedHashMap;
import java.util.Map;

import io.cradesk.api.reporting.ReportType;

public class TemplateDraftGenerator implements DraftGenerator {

    // Devuelve el nombre con el que se guarda un borrador hecho con la plantilla.
    @Override
    public String name() {
        return "TEMPLATE";
    }

    // Arma un borrador determinista con los datos del evento y marca lo que la persona debe completar.
    @Override
    public DraftContent generate(ReportType type, DraftContext c) {
        String product = c.productName() + " " + c.version();
        String exploited = "Yes. " + c.cveId() + " is listed in the CISA Known Exploited Vulnerabilities catalog since "
                + c.kevDateAdded() + "; exploitation confirmed by " + c.organizationName() + " on " + c.confirmedAt()
                + ".";
        String description = c.cveId() + " (" + c.vulnerabilityId() + "): " + orPending(c.summary())
                + ". Severity: " + orPending(c.severity()) + ". Affected components: "
                + String.join("; ", c.affectedComponents()) + ".";
        Map<String, String> values = new LinkedHashMap<>();
        values.put("summary", "Actively exploited vulnerability " + c.cveId() + " affects " + product + ".");
        values.put("activelyExploited", exploited);
        values.put("suspectedMaliciousAct", TO_BE_COMPLETED);
        values.put("memberStatesAffected", TO_BE_COMPLETED);
        values.put("immediateMeasures", TO_BE_COMPLETED);
        values.put("vulnerabilityDescription", description);
        values.put("generalNature", "Vulnerability in the third-party component(s) "
                + String.join("; ", c.affectedComponents()) + " shipped with " + product + ".");
        values.put("correctiveMeasuresAvailable", TO_BE_COMPLETED);
        values.put("mitigationsForUsers", TO_BE_COMPLETED);
        values.put("sensitivity", TO_BE_COMPLETED);
        values.put("severityAndImpact", "Severity: " + orPending(c.severity()) + ". Impact: " + TO_BE_COMPLETED);
        values.put("maliciousActorInformation", TO_BE_COMPLETED);
        values.put("securityUpdateDetails", TO_BE_COMPLETED);
        values.put("lessonsLearned", TO_BE_COMPLETED);
        Map<String, String> selected = new LinkedHashMap<>();
        DraftContent.fieldsOf(type).forEach(field -> selected.put(field, values.get(field)));
        return new DraftContent(type, selected);
    }

    // Devuelve el texto o la marca de "por completar" si falta.
    private static String orPending(String value) {
        return value == null || value.isBlank() ? TO_BE_COMPLETED : value;
    }
}
