package io.cradesk.api.sbom;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cradesk.sbom")
public record SbomProperties(long maxBytes) {

    // Valida al arrancar que el límite de tamaño del SBOM sea positivo.
    public SbomProperties {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("cradesk.sbom.max-bytes must be positive");
        }
    }
}
