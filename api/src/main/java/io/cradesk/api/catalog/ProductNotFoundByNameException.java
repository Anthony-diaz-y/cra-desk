package io.cradesk.api.catalog;

import io.cradesk.api.common.NotFoundException;

public class ProductNotFoundByNameException extends NotFoundException {

    // Crea el error de producto que no existe con ese nombre en la empresa.
    public ProductNotFoundByNameException(String name) {
        super("Product '" + name + "' not found");
    }
}
