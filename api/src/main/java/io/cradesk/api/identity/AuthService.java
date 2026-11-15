package io.cradesk.api.identity;

import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;
    private final DemoProperties demo;
    private final String dummyHash;

    // Recibe sus dependencias y prepara un hash ficticio para igualar el tiempo de respuesta.
    public AuthService(UserAccountRepository users, OrganizationRepository organizations,
            PasswordEncoder passwordEncoder, SecurityContextRepository securityContextRepository, Clock clock,
            DemoProperties demo) {
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.clock = clock;
        this.demo = demo;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-equal-timing");
    }

    // Valida email y contraseña, registra los fallos y abre la sesión si todo es correcto.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public void login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Instant now = clock.instant();
        UserAccount user = users.findForLoginByEmail(Emails.normalize(request.email())).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (user.isLockedAt(now)) {
            throw new InvalidCredentialsException();
        }
        if (!passwordMatches) {
            user.recordFailedLogin(now);
            throw new InvalidCredentialsException();
        }
        user.recordSuccessfulLogin();
        startSession(principalOf(user), httpRequest, httpResponse);
    }

    // Abre una sesión como el usuario demo de solo lectura, o lanza 404 si la demo está desactivada.
    @Transactional(readOnly = true)
    public void demoLogin(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (!demo.enabled()) {
            throw new DemoDisabledException();
        }
        UserAccount user = users.findByEmail(DemoProperties.EMAIL)
                .filter(u -> u.getRole() == AccessRole.DEMO)
                .orElseThrow(DemoDisabledException::new);
        startSession(principalOf(user), httpRequest, httpResponse);
    }

    // Autentica al principal, cambia el id de sesión y guarda el contexto de seguridad en la sesión.
    public void startSession(TenantPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        securityContextRepository.saveContext(context, request, response);
    }

    // Arma la respuesta con los datos del usuario y la empresa de la sesión actual.
    @Transactional(readOnly = true)
    public MeResponse me(TenantPrincipal principal) {
        String organizationName = organizations.findById(principal.organizationId())
                .map(Organization::getName)
                .orElseThrow(() -> new IllegalStateException("Organization of the session no longer exists"));
        return new MeResponse(principal.userId(), principal.email(), principal.organizationId(), organizationName,
                principal.role());
    }

    // Convierte un usuario guardado en el principal que usa Spring Security.
    static TenantPrincipal principalOf(UserAccount user) {
        return new TenantPrincipal(user.getOrganization().getId(), user.getId(), user.getEmail(), user.getRole());
    }
}
