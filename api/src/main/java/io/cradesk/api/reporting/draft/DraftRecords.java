package io.cradesk.api.reporting.draft;

public final class DraftRecords {

    // Constructor privado: solo agrupa los records que describen los campos de cada reporte para la IA.
    private DraftRecords() {
    }

    public record EarlyWarningDraft(String summary, String activelyExploited, String suspectedMaliciousAct,
            String memberStatesAffected, String immediateMeasures) {
    }

    public record NotificationDraft(String summary, String activelyExploited, String suspectedMaliciousAct,
            String memberStatesAffected, String immediateMeasures, String vulnerabilityDescription,
            String generalNature, String correctiveMeasuresAvailable, String mitigationsForUsers,
            String sensitivity) {
    }

    public record FinalReportDraft(String vulnerabilityDescription, String severityAndImpact,
            String maliciousActorInformation, String securityUpdateDetails, String lessonsLearned) {
    }
}
