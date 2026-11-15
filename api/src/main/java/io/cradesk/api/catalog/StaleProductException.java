package io.cradesk.api.catalog;

import io.cradesk.api.common.ConflictException;

public class StaleProductException extends ConflictException {

    // Crea el error de producto modificado por otra persona desde que se leyó.
    public StaleProductException() {
        super("Product was modified by someone else");
    }
}
