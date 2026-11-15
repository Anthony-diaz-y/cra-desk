package io.cradesk.api.sbom;

public record ParsedComponent(String bomRef, String type, String group, String name, String version, String purl) {
}
