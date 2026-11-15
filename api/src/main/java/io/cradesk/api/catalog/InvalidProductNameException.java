package io.cradesk.api.catalog;

import io.cradesk.api.common.BadRequestException;

public class InvalidProductNameException extends BadRequestException {

    // Crea el error de nombre que queda vacío al quitarle los espacios.
    public InvalidProductNameException() {
        super("Product name must contain visible characters");
    }
}
