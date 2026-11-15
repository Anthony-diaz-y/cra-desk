package io.cradesk.api.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final InvitationService invitations;
    private final CurrentTenant tenant;

    // Recibe los servicios de autenticación e invitaciones y el acceso al usuario actual.
    public AuthController(AuthService auth, InvitationService invitations, CurrentTenant tenant) {
        this.auth = auth;
        this.invitations = invitations;
        this.tenant = tenant;
    }

    // Genera el token CSRF para que quede en la cookie XSRF-TOKEN y responde 204.
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    // Inicia sesión con email y contraseña y responde 204.
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        auth.login(request, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    // Abre la sesión demo de solo lectura y responde 204.
    @PostMapping("/demo")
    public ResponseEntity<Void> demo(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        auth.demoLogin(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    // Acepta una invitación, crea la cuenta y la empresa, abre la sesión y responde 201.
    @PostMapping("/accept-invitation")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return invitations.accept(request, httpRequest, httpResponse);
    }

    // Devuelve los datos del usuario y la empresa de la sesión actual.
    @GetMapping("/me")
    public MeResponse me() {
        return auth.me(tenant.principal());
    }
}
