package io.cradesk.api.identity;

import io.cradesk.api.common.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {

    // Crea el error genérico de login que no revela si el email existe.
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
