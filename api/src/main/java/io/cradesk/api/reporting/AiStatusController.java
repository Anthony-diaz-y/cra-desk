package io.cradesk.api.reporting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.reporting.draft.DraftGenerator;
import io.cradesk.api.reporting.draft.DraftGenerators;

@RestController
public class AiStatusController {

    static final String TEMPLATE = "TEMPLATE";

    private final DraftGenerators generators;
    private final AiQuota quota;
    private final CurrentTenant tenant;

    // Recibe los generadores de borradores, el cupo diario y la empresa actual.
    public AiStatusController(DraftGenerators generators, AiQuota quota, CurrentTenant tenant) {
        this.generators = generators;
        this.quota = quota;
        this.tenant = tenant;
    }

    // Indica si hay un proveedor de IA configurado y cuántos borradores con IA le quedan hoy a la empresa.
    @GetMapping("/api/reporting/ai")
    public AiStatus status() {
        int limit = generators.dailyLimit();
        int remaining = Math.max(0, limit - quota.usedToday(tenant.organizationId()));
        return generators.ai()
                .map(DraftGenerator::name)
                .map(name -> new AiStatus(true, name, generators.model(), limit, remaining))
                .orElseGet(() -> new AiStatus(false, TEMPLATE, "", limit, remaining));
    }

    public record AiStatus(boolean available, String provider, String model, int dailyLimit, int remainingToday) {
    }
}
