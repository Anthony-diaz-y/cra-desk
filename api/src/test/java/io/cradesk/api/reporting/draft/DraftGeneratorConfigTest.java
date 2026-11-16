package io.cradesk.api.reporting.draft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class DraftGeneratorConfigTest {

    final JsonMapper json = JsonMapper.builder().build();

    // Arma las propiedades de IA de prueba con proveedor y claves indicadas.
    private static AiProperties props(String provider, String apiKey, String anthropicKey) {
        return new AiProperties(provider, "https://api.deepseek.com", "", apiKey, anthropicKey, 20);
    }

    // Comprueba que con el proveedor template no hay IA y siempre existe la plantilla.
    @Test
    void templateProviderHasNoAi() {
        DraftGenerators generators = DraftGeneratorConfig.select(props("template", "", ""), json);

        assertThat(generators.ai()).isEmpty();
        assertThat(generators.template().name()).isEqualTo("TEMPLATE");
    }

    // Comprueba que el proveedor compatible con OpenAI se activa con su clave y usa deepseek-chat por defecto.
    @Test
    void openAiCompatibleWithKeyIsSelected() {
        DraftGenerators generators = DraftGeneratorConfig.select(props("openai-compatible", "sk-x", ""), json);

        assertThat(generators.ai()).get().isInstanceOf(OpenAiCompatibleDraftGenerator.class);
        assertThat(generators.model()).isEqualTo("deepseek-chat");
    }

    // Comprueba que Claude se activa con ANTHROPIC_API_KEY y usa claude-haiku-5-5 por defecto.
    @Test
    void claudeWithKeyIsSelected() {
        DraftGenerators generators = DraftGeneratorConfig.select(props("claude", "", "sk-ant-x"), json);

        assertThat(generators.ai()).get().isInstanceOf(ClaudeDraftGenerator.class);
        assertThat(generators.model()).isEqualTo("claude-haiku-5-5");
    }

    // Comprueba que un proveedor elegido sin su clave cae en la plantilla en vez de impedir el arranque.
    @Test
    void providerWithoutKeyFallsBackToTemplate() {
        assertThat(DraftGeneratorConfig.select(props("claude", "", ""), json).ai()).isEmpty();
        assertThat(DraftGeneratorConfig.select(props("openai-compatible", "", ""), json).ai()).isEmpty();
        assertThat(DraftGeneratorConfig.select(props("something-else", "x", "y"), json).ai()).isEmpty();
    }
}
