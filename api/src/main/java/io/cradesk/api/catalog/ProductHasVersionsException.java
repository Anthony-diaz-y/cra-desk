package io.cradesk.api.catalog;

import java.util.UUID;

import io.cradesk.api.common.ConflictException;

public class ProductHasVersionsException extends ConflictException {
    // Crea el error de borrar un producto que ya tiene versiones.
    public ProductHasVersionsException(UUID id) {
        super("Product " + id + " has versions and cannot be deleted");
    }
}
