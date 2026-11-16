package io.cradesk.api.reporting.draft;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.cradesk.api.reporting.ReportType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OpenAiCompatibleDraftGenerator implements DraftGenerator {

    private final RestClient http;
    private final String model;
    private final String apiKey;
    private final JsonMapper json;

    // Recibe el cliente HTTP con la URL base del proveedor, el modelo, la clave y el mapper JSON.
    public OpenAiCompatibleDraftGenerator(RestClient http, String model, String apiKey, JsonMapper json) {
        this.http = http;
        this.model = model;
        this.apiKey = apiKey;
        this.json = json;
    }

    // Devuelve el nombre con el que se guarda un borrador hecho con este proveedor.
    @Override
    public String name() {
        return "OPENAI_COMPATIBLE";
    }

    // Pide el borrador en modo JSON al endpoint chat/completions y lo valida contra los campos del tipo.
    @Override
    public DraftContent generate(ReportType type, DraftContext context) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", DraftPrompt.system(type)),
                        Map.of("role", "user", "content", DraftPrompt.user(context))));
        JsonNode response;
        try {
            response = http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new DraftGenerationException("AI provider: HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new DraftGenerationException("AI provider: " + e.getClass().getSimpleName(), e);
        }
        String content = response == null ? null
                : response.path("choices").path(0).path("message").path("content").asString(null);
        try {
            return DraftContent.parse(type, content, json);
        } catch (InvalidDraftException e) {
            throw new DraftGenerationException("AI provider: invalid draft " + e.errors().keySet(), e);
        }
    }
}
