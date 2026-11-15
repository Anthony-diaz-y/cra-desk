package io.cradesk.api.sbom;

import io.cradesk.api.common.NotFoundException;

public class SbomNotFoundException extends NotFoundException {

    // Crea el error de versión que todavía no tiene SBOM.
    public SbomNotFoundException() {
        super("No SBOM uploaded for this version");
    }
}
