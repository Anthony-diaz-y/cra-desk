package io.cradesk.api.reporting.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.cradesk.api.reporting.ReportType;
import tools.jackson.databind.json.JsonMapper;

class OpenAiCompatibleDraftGeneratorTest {

    static final String BASE = "https://llm.test";

    MockRestServiceServer server;
    OpenAiCompatibleDraftGenerator generator;
    final JsonMapper json = JsonMapper.builder().build();

    // Crea el generador apuntando a un servidor simulado con una clave de prueba.
    @BeforeEach
    void createGenerator() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new OpenAiCompatibleDraftGenerator(builder.build(), "deepseek-chat", "sk-test-key", json);
    }

    // Comprueba que pide JSON al modelo configurado con la clave y lee el borrador de la respuesta.
    @Test
    void sendsModelJsonModeAndKeyThenParsesTheDraft() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-key"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(completion(DraftTestData.earlyWarningJson()), MediaType.APPLICATION_JSON));

        DraftContent content = generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core"));

        assertThat(content.fields().get("immediateMeasures")).isEqualTo("Disable JNDI lookups");
        assertThat(generator.name()).isEqualTo("OPENAI_COMPATIBLE");
        server.verify();
    }

    // Comprueba que una clave rechazada se convierte en un error corto de generación.
    @Test
    void unauthorizedBecomesDraftGenerationException() {
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        DraftGenerationException e = catchThrowableOfType(DraftGenerationException.class,
                () -> generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core")));

        assertThat(e).hasMessage("AI provider: HTTP 401");
    }

    // Comprueba que un borrador con campos faltantes se rechaza como error de generación.
    @Test
    void incompleteDraftBecomesDraftGenerationException() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(completion("{\"summary\":\"only this\"}"), MediaType.APPLICATION_JSON));

        DraftGenerationException e = catchThrowableOfType(DraftGenerationException.class,
                () -> generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core")));

        assertThat(e).hasMessageStartingWith("AI provider: invalid draft");
    }

    // Comprueba que una respuesta que no es JSON se rechaza como error de generación.
    @Test
    void nonJsonAnswerBecomesDraftGenerationException() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(completion("Sure! Here is your report"), MediaType.APPLICATION_JSON));

        DraftGenerationException e = catchThrowableOfType(DraftGenerationException.class,
                () -> generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core")));

        assertThat(e).hasMessageStartingWith("AI provider: invalid draft");
    }

    // Arma una respuesta de chat completions con ese contenido de mensaje.
    private String completion(String content) {
        return json.writeValueAsString(java.util.Map.of("choices",
                java.util.List.of(java.util.Map.of("message", java.util.Map.of("role", "assistant", "content", content)))));
    }
}
