package io.cradesk.api.vulns;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.kev")
public record KevProperties(String url) {
}
