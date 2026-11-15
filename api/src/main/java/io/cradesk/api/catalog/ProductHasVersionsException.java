package io.cradesk.api.catalog;

import java.util.UUID;

/** Versions are compliance records, so a product that has them cannot be deleted. */
public class ProductHasVersionsException extends RuntimeException {
    public ProductHasVersionsException(UUID id) {
        super("Product " + id + " has versions and cannot be deleted");
    }
}
