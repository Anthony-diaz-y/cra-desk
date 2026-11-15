package io.cradesk.api.catalog;

public class DuplicateVersionException extends RuntimeException {
    public DuplicateVersionException(String version) {
        super("Version '" + version + "' already exists for this product");
    }
}
