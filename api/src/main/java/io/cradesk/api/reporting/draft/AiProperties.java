package io.cradesk.api.reporting.draft;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.ai")
public record AiProperties(String provider, String baseUrl, String model, String apiKey, String anthropicApiKey,
        int dailyLimit) {
}
