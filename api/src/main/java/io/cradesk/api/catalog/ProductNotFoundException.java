package io.cradesk.api.catalog;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {
    // Crea el error de producto inexistente.
    public ProductNotFoundException(UUID id) {
        super("Product " + id + " not found");
    }
}
