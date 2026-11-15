package io.cradesk.api.sbom;

import java.util.List;

public record ParsedSbom(String specVersion, String serialNumber, Integer bomVersion,
        List<ParsedComponent> components) {

    // Cuenta cuántos componentes no traen purl.
    public int componentsWithoutPurl() {
        return (int) components.stream().filter(c -> c.purl() == null).count();
    }
}
