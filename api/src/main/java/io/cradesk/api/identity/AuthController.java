package io.cradesk.api.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentTenant tenant;

    // Recibe el servicio de autenticación y el acceso al usuario actual.
    public AuthController(AuthService auth, CurrentTenant tenant) {
        this.auth = auth;
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

    // Devuelve los datos del usuario y la empresa de la sesión actual.
    @GetMapping("/me")
    public MeResponse me() {
        return auth.me(tenant.principal());
    }
}
