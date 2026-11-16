package io.cradesk.api.reporting.draft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cradesk.api.reporting.ReportType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record DraftContent(ReportType type, Map<String, String> fields) {

    public static final int MAX_FIELD_LENGTH = 4000;

    private static final List<String> EARLY_WARNING = List.of("summary", "activelyExploited",
            "suspectedMaliciousAct", "memberStatesAffected", "immediateMeasures");
    private static final List<String> NOTIFICATION = List.of("summary", "activelyExploited",
            "suspectedMaliciousAct", "memberStatesAffected", "immediateMeasures", "vulnerabilityDescription",
            "generalNature", "correctiveMeasuresAvailable", "mitigationsForUsers", "sensitivity");
    private static final List<String> FINAL_REPORT = List.of("vulnerabilityDescription", "severityAndImpact",
            "maliciousActorInformation", "securityUpdateDetails", "lessonsLearned");

    // Guarda una copia ordenada e inmutable de los campos.
    public DraftContent {
        fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    // Devuelve los campos que exige el art. 14 del CRA para ese tipo de reporte, en orden.
    public static List<String> fieldsOf(ReportType type) {
        return switch (type) {
            case EARLY_WARNING -> EARLY_WARNING;
            case NOTIFICATION -> NOTIFICATION;
            case FINAL_REPORT -> FINAL_REPORT;
        };
    }

    // Lee el JSON de un reporte y exige exactamente sus campos, todos de texto y de largo razonable.
    public static DraftContent parse(ReportType type, String text, JsonMapper json) {
        JsonNode root;
        try {
            root = text == null ? null : json.readTree(text);
        } catch (JacksonException e) {
            root = null;
        }
        if (root == null || !root.isObject()) {
            throw new InvalidDraftException(Map.of("content", "must be a JSON object"));
        }
        return of(type, root);
    }

    // Valida un objeto JSON contra los campos del tipo y devuelve el contenido o los errores por campo.
    public static DraftContent of(ReportType type, JsonNode root) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> values = new LinkedHashMap<>();
        List<String> expected = fieldsOf(type);
        for (String name : root.propertyNames()) {
            if (!expected.contains(name)) {
                errors.put(name, "is not a field of " + type);
            }
        }
        for (String name : expected) {
            JsonNode value = root.get(name);
            if (value == null || value.isNull()) {
                errors.put(name, "is required");
            } else if (!value.isString()) {
                errors.put(name, "must be text");
            } else if (value.stringValue().length() > MAX_FIELD_LENGTH) {
                errors.put(name, "must be at most " + MAX_FIELD_LENGTH + " characters");
            } else {
                values.put(name, value.stringValue().strip());
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidDraftException(errors);
        }
        return new DraftContent(type, values);
    }

    // Escribe los campos como un objeto JSON en el orden del tipo.
    public String toJson(JsonMapper json) {
        return json.writeValueAsString(fields);
    }
}
