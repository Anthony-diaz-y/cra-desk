package io.cradesk.api.catalog;

import java.util.UUID;

public class VersionNotFoundException extends RuntimeException {
    // Crea el error de versión inexistente para ese producto.
    public VersionNotFoundException(UUID versionId) {
        super("Version " + versionId + " not found for this product");
    }
}
