package io.cradesk.api.reporting.draft;

import java.util.Optional;

public record DraftGenerators(Optional<DraftGenerator> ai, TemplateDraftGenerator template, String model,
        int dailyLimit) {
}
