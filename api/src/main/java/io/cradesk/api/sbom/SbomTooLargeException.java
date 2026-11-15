package io.cradesk.api.sbom;

import io.cradesk.api.common.PayloadTooLargeException;

public class SbomTooLargeException extends PayloadTooLargeException {

    // Crea el error de SBOM que supera el tamaño máximo permitido, indicado en MB.
    public SbomTooLargeException(long maxBytes) {
        super("SBOM larger than " + (maxBytes / (1024 * 1024)) + " MB");
    }
}
