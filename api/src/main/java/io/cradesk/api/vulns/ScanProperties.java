package io.cradesk.api.vulns;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.scan")
public record ScanProperties(boolean schedulerEnabled, Duration pollInterval, String dailyCron) {
}
