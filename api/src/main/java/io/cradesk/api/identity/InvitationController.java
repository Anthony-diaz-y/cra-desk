package io.cradesk.api.identity;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invitations")
public class InvitationController {

    private final InvitationService invitations;

    // Recibe el servicio de invitaciones por inyección de dependencias.
    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    // Crea una invitación (solo admin) y responde 201 con el código, que no se vuelve a mostrar.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationCreatedResponse create(@Valid @RequestBody CreateInvitationRequest request) {
        return invitations.create(request);
    }
}
