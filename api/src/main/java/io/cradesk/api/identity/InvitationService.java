package io.cradesk.api.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.common.Constraints;

@Service
public class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);
    static final int CODE_BYTES = 32;

    private final InvitationRepository invitations;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService auth;
    private final Clock clock;

    // Recibe los repositorios, el codificador de contraseñas, el servicio de sesión y el reloj.
    public InvitationService(InvitationRepository invitations, OrganizationRepository organizations,
            UserAccountRepository users, PasswordEncoder passwordEncoder, AuthService auth, Clock clock) {
        this.invitations = invitations;
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auth = auth;
        this.clock = clock;
    }

    // Crea una invitación de un solo uso para un email sin cuenta y devuelve el código una única vez.
    @Transactional
    public InvitationCreatedResponse create(CreateInvitationRequest request) {
        String email = Emails.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        String code = SecretTokens.random(CODE_BYTES);
        Invitation invitation = invitations.save(
                new Invitation(email, SecretTokens.sha256Hex(code), clock.instant().plus(VALIDITY)));
        return new InvitationCreatedResponse(invitation.getId(), invitation.getEmail(), invitation.getExpiresAt(),
                code);
    }

    // Valida la invitación, crea la empresa y el usuario, la marca como usada y abre la sesión.
    @Transactional
    public MeResponse accept(AcceptInvitationRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Instant now = clock.instant();
        String email = Emails.normalize(request.email());
        Invitation invitation = invitations.findByCodeHash(SecretTokens.sha256Hex(request.code()))
                .filter(i -> i.isUsableBy(email, now))
                .orElseThrow(InvalidInvitationException::new);
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        Organization organization = organizations.save(new Organization(request.organizationName().strip()));
        UserAccount user = saveUser(new UserAccount(organization, email,
                passwordEncoder.encode(request.password()), AccessRole.MEMBER));
        invitation.markAccepted(now);
        TenantPrincipal principal = AuthService.principalOf(user);
        auth.startSession(principal, httpRequest, httpResponse);
        return new MeResponse(user.getId(), user.getEmail(), organization.getId(), organization.getName(),
                user.getRole());
    }

    // Guarda el usuario y convierte un email repetido detectado por la base de datos en error 409.
    private UserAccount saveUser(UserAccount user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, UserAccount.EMAIL_UNIQUE)) {
                throw new EmailAlreadyRegisteredException(user.getEmail());
            }
            throw e;
        }
    }
}
