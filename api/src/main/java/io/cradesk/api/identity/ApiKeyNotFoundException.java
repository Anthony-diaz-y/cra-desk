package io.cradesk.api.identity;

import java.util.UUID;

import io.cradesk.api.common.NotFoundException;

public class ApiKeyNotFoundException extends NotFoundException {

    // Crea el error de clave de API inexistente o de otra empresa.
    public ApiKeyNotFoundException(UUID id) {
        super("API key " + id + " not found");
    }
}
