package io.cradesk.api.identity;

import io.cradesk.api.common.BadRequestException;

public class InvalidInvitationException extends BadRequestException {

    // Crea el error genérico de invitación que no revela si el código existe, venció o ya se usó.
    public InvalidInvitationException() {
        super("Invitation is invalid or expired");
    }
}
