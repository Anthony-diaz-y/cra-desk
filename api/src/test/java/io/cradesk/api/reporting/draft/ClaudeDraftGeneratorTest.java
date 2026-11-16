package io.cradesk.api.reporting.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;

import io.cradesk.api.reporting.ReportType;
import tools.jackson.databind.json.JsonMapper;

class ClaudeDraftGeneratorTest {

    HttpServer server;
    final AtomicReference<String> lastRequest = new AtomicReference<>();
    final JsonMapper json = JsonMapper.builder().build();

    // Detiene la API falsa después de cada test.
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Comprueba que pide el borrador a Claude Haiku 5.5 con salida estructurada y lo convierte en contenido.
    @Test
    void asksHaikuForStructuredOutputAndParsesTheDraft() throws IOException {
        ClaudeDraftGenerator generator = generatorAnswering(message("end_turn", DraftTestData.earlyWarningJson()));

        DraftContent content = generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core"));

        assertThat(content.fields().get("summary")).isEqualTo("Log4Shell in Smart Lock 1.0.0");
        assertThat(lastRequest.get()).contains("\"model\":\"claude-haiku-5-5\"").contains("output_config");
        assertThat(generator.name()).isEqualTo("CLAUDE");
    }

    // Comprueba que una negativa del modelo se convierte en un error de generación.
    @Test
    void refusalBecomesDraftGenerationException() throws IOException {
        ClaudeDraftGenerator generator = generatorAnswering(message("refusal", "{}"));

        DraftGenerationException e = catchThrowableOfType(DraftGenerationException.class,
                () -> generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core")));

        assertThat(e).hasMessageStartingWith("AI provider: stopped (refusal)");
    }

    // Comprueba que una clave rechazada por la API se convierte en un error de generación.
    @Test
    void unauthorizedBecomesDraftGenerationException() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        ClaudeDraftGenerator generator = newGenerator();

        DraftGenerationException e = catchThrowableOfType(DraftGenerationException.class,
                () -> generator.generate(ReportType.EARLY_WARNING, DraftTestData.context("log4j-core")));

        assertThat(e).hasMessageStartingWith("AI provider:");
    }

    // Arranca una API falsa que guarda la petición y responde siempre ese mensaje.
    private ClaudeDraftGenerator generatorAnswering(String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return newGenerator();
    }

    // Crea el generador con un cliente del SDK apuntando a la API falsa y sin reintentos.
    private ClaudeDraftGenerator newGenerator() {
        return new ClaudeDraftGenerator(AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0)
                .build(), "claude-haiku-5-5", json);
    }

    // Arma una respuesta de la Messages API con ese motivo de parada y ese texto.
    private String message(String stopReason, String text) {
        return json.writeValueAsString(java.util.Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-haiku-5-5",
                "content", java.util.List.of(java.util.Map.of("type", "text", "text", text)),
                "stop_reason", stopReason,
                "usage", java.util.Map.of("input_tokens", 100, "output_tokens", 50)));
    }
}
