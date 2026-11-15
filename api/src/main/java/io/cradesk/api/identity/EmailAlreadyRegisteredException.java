package io.cradesk.api.identity;

import io.cradesk.api.common.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

    // Crea el error de email que ya tiene una cuenta.
    public EmailAlreadyRegisteredException(String email) {
        super("An account for '" + email + "' already exists");
    }
}
