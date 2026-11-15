package io.cradesk.api.catalog;

import io.cradesk.api.common.ConflictException;

public class DuplicateVersionException extends ConflictException {
    // Crea el error de versión repetida en el mismo producto.
    public DuplicateVersionException(String version) {
        super("Version '" + version + "' already exists for this product");
    }
}
