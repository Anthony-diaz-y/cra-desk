package io.cradesk.api.reporting.draft;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class DraftTestData {

    // Constructor privado: solo agrupa datos de prueba.
    private DraftTestData() {
    }

    // Devuelve un contexto de borrador de ejemplo para la cerradura con Log4Shell.
    static DraftContext context(String componentName) {
        return new DraftContext("Acme GmbH", "Smart Lock", "IMPORTANT_CLASS_I", "1.0.0", "CVE-2021-44228",
                "GHSA-jfh8-c2jp-5v3q", "Remote code injection in Log4j", "CRITICAL", LocalDate.of(2021, 12, 10),
                "Apache", "Log4j2", List.of(componentName), Instant.parse("2026-11-15T10:00:00Z"));
    }

    // Devuelve un JSON válido de aviso temprano.
    static String earlyWarningJson() {
        return """
                {"summary":"Log4Shell in Smart Lock 1.0.0","activelyExploited":"Yes, listed in CISA KEV",
                 "suspectedMaliciousAct":"TO BE COMPLETED","memberStatesAffected":"TO BE COMPLETED",
                 "immediateMeasures":"Disable JNDI lookups"}
                """;
    }
}
