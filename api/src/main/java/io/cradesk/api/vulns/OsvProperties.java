package io.cradesk.api.vulns;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.osv")
public record OsvProperties(String baseUrl) {
}
