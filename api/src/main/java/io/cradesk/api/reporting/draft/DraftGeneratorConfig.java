package io.cradesk.api.reporting.draft;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class DraftGeneratorConfig {

    static final String DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5";
    static final String DEFAULT_OPENAI_MODEL = "deepseek-chat";
    static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(DraftGeneratorConfig.class);

    // Crea el conjunto de generadores de borradores según la configuración de IA.
    @Bean
    DraftGenerators draftGenerators(AiProperties properties, JsonMapper json) {
        DraftGenerators generators = select(properties, json);
        log.info("Report drafts: {}", generators.ai().map(g -> g.name() + " (" + generators.model() + ")")
                .orElse("TEMPLATE only"));
        return generators;
    }

    // Elige el proveedor de IA configurado si tiene su clave; si no, deja solo la plantilla.
    static DraftGenerators select(AiProperties p, JsonMapper json) {
        String provider = p.provider() == null ? "template" : p.provider().strip().toLowerCase(java.util.Locale.ROOT);
        TemplateDraftGenerator template = new TemplateDraftGenerator();
        switch (provider) {
            case "claude" -> {
                if (!blank(p.anthropicApiKey())) {
                    String model = blank(p.model()) ? DEFAULT_CLAUDE_MODEL : p.model();
                    return new DraftGenerators(Optional.of(new ClaudeDraftGenerator(
                            claudeClient(p.anthropicApiKey(), null), model, json)), template, model, p.dailyLimit());
                }
                log.warn("CRADESK_AI_PROVIDER=claude but ANTHROPIC_API_KEY is not set; using the template");
            }
            case "openai-compatible" -> {
                if (!blank(p.apiKey()) && !blank(p.baseUrl())) {
                    String model = blank(p.model()) ? DEFAULT_OPENAI_MODEL : p.model();
                    return new DraftGenerators(Optional.of(new OpenAiCompatibleDraftGenerator(restClient(p.baseUrl()),
                            model, p.apiKey(), json)), template, model, p.dailyLimit());
                }
                log.warn("CRADESK_AI_PROVIDER=openai-compatible needs CRADESK_AI_API_KEY and CRADESK_AI_BASE_URL;"
                        + " using the template");
            }
            case "template" -> {
            }
            default -> log.warn("Unknown CRADESK_AI_PROVIDER '{}'; using the template", provider);
        }
        return new DraftGenerators(Optional.empty(), template, "", p.dailyLimit());
    }

    // Crea el cliente del SDK de Anthropic con el timeout del proveedor y sin reintentos, apuntando a otra URL si se indica.
    static AnthropicClient claudeClient(String apiKey, String baseUrl) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(TIMEOUT)
                .maxRetries(0);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    // Crea un RestClient para el proveedor con timeouts de 5 s para conectar y 60 s para leer.
    private static RestClient restClient(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", "")).requestFactory(factory).build();
    }

    // Indica si un texto falta o está en blanco.
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
