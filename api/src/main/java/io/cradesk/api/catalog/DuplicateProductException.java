package io.cradesk.api.catalog;

public class DuplicateProductException extends RuntimeException {
    // Crea el error de producto con nombre repetido.
    public DuplicateProductException(String name) {
        super("A product named '" + name + "' already exists");
    }
}
