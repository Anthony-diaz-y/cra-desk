package io.cradesk.api.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.demo")
public record DemoProperties(boolean enabled) {

    public static final String ORGANIZATION_NAME = "Demo Manufacturer (read-only)";
    public static final String EMAIL = "demo@cradesk.invalid";
}
