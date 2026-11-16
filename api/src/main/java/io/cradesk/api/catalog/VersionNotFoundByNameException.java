package io.cradesk.api.catalog;

import io.cradesk.api.common.NotFoundException;

public class VersionNotFoundByNameException extends NotFoundException {

    // Crea el error de versión que no existe para ese producto.
    public VersionNotFoundByNameException(String version, String product) {
        super("Version '" + version + "' not found for product '" + product + "'");
    }
}
