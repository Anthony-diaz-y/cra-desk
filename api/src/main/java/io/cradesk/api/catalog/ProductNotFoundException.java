package io.cradesk.api.catalog;

import java.util.UUID;

import io.cradesk.api.common.NotFoundException;

public class ProductNotFoundException extends NotFoundException {
    // Crea el error de producto inexistente.
    public ProductNotFoundException(UUID id) {
        super("Product " + id + " not found");
    }
}
