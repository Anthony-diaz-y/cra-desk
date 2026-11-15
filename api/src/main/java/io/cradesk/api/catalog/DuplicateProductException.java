package io.cradesk.api.catalog;

import io.cradesk.api.common.ConflictException;

public class DuplicateProductException extends ConflictException {
    // Crea el error de producto con nombre repetido.
    public DuplicateProductException(String name) {
        super("A product named '" + name + "' already exists");
    }
}
