package io.cradesk.api.support;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.cradesk.api.reporting.ReportType;
import io.cradesk.api.reporting.draft.DraftContent;
import io.cradesk.api.reporting.draft.DraftContext;
import io.cradesk.api.reporting.draft.DraftGenerationException;
import io.cradesk.api.reporting.draft.DraftGenerator;
import io.cradesk.api.reporting.draft.DraftGenerators;
import io.cradesk.api.reporting.draft.TemplateDraftGenerator;

@TestConfiguration(proxyBeanMethods = false)
public class FakeAi {

    public static final int DAILY_LIMIT = 20;

    // Crea el doble del proveedor de IA que usan los tests.
    @Bean
    FakeDraftGenerator fakeDraftGenerator() {
        return new FakeDraftGenerator();
    }

    // Reemplaza los generadores reales por la plantilla más el doble de IA, con el tope diario de 20.
    @Bean
    @Primary
    DraftGenerators fakeDraftGenerators(FakeDraftGenerator fake) {
        return new DraftGenerators(Optional.of(fake), new TemplateDraftGenerator(), "fake-model", DAILY_LIMIT);
    }

    public static class FakeDraftGenerator implements DraftGenerator {

        private final TemplateDraftGenerator template = new TemplateDraftGenerator();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String failure;
        private volatile boolean crash;

        // Programa que la próxima generación falle con ese motivo.
        public void willFail(String reason) {
            failure = reason;
        }

        // Programa que la próxima generación falle con un error inesperado, no con DraftGenerationException.
        public void willCrash() {
            crash = true;
        }

        // Devuelve cuántas veces se pidió un borrador a la IA.
        public int calls() {
            return calls.get();
        }

        // Vuelve al comportamiento normal y pone el contador en cero.
        public void reset() {
            failure = null;
            crash = false;
            calls.set(0);
        }

        // Devuelve el nombre con el que se guarda un borrador del doble de IA.
        @Override
        public String name() {
            return "FAKE_AI";
        }

        // Devuelve un borrador como el de la plantilla con un resumen distinto, o falla si así se programó.
        @Override
        public DraftContent generate(ReportType type, DraftContext context) {
            calls.incrementAndGet();
            if (crash) {
                throw new IllegalStateException("bug in the provider client");
            }
            if (failure != null) {
                throw new DraftGenerationException(failure);
            }
            DraftContent base = template.generate(type, context);
            java.util.Map<String, String> fields = new java.util.LinkedHashMap<>(base.fields());
            fields.replaceAll((field, value) -> field.equals("summary") || field.equals("vulnerabilityDescription")
                    ? "AI: " + value : value);
            return new DraftContent(type, fields);
        }
    }
}
