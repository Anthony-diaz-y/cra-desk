package io.cradesk.api.catalog;

public class DuplicateVersionException extends RuntimeException {
    // Crea el error de versión repetida en el mismo producto.
    public DuplicateVersionException(String version) {
        super("Version '" + version + "' already exists for this product");
    }
}
