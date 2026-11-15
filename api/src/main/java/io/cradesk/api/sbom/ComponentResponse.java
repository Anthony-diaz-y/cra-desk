package io.cradesk.api.sbom;

public record ComponentResponse(String type, String group, String name, String version, String purl,
        String bomRef) {

    // Convierte un componente guardado en su respuesta JSON.
    static ComponentResponse of(SbomComponent c) {
        return new ComponentResponse(c.getType(), c.getGroup(), c.getName(), c.getVersion(), c.getPurl(),
                c.getBomRef());
    }
}
