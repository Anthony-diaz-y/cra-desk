package io.cradesk.api.catalog;

import java.util.UUID;

import io.cradesk.api.common.NotFoundException;

public class VersionNotFoundException extends NotFoundException {
    // Crea el error de versión inexistente para ese producto.
    public VersionNotFoundException(UUID versionId) {
        super("Version " + versionId + " not found for this product");
    }
}
