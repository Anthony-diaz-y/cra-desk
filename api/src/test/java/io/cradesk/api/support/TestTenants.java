package io.cradesk.api.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.Organization;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.identity.UserAccount;
import io.cradesk.api.identity.UserAccountRepository;

@TestComponent
public class TestTenants {

    public static final String PASSWORD = "correct-horse-battery";
    public static final String ADMIN_TOKEN = "test-admin-token-0123456789-abcdefghijklmnop";

    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final WebApplicationContext context;

    // Recibe los repositorios, el codificador de contraseñas y el contexto web por inyección.
    public TestTenants(OrganizationRepository organizations, UserAccountRepository users,
            PasswordEncoder passwordEncoder, WebApplicationContext context) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.context = context;
    }

    // Guarda una empresa con un usuario MEMBER y devuelve sus datos para los tests.
    public TestTenant create(String organizationName) {
        Organization organization = organizations.saveAndFlush(new Organization(organizationName));
        String email = organizationName.toLowerCase(Locale.ROOT).replace(" ", "") + "@example.com";
        UserAccount user = users.saveAndFlush(
                new UserAccount(organization, email, passwordEncoder.encode(PASSWORD), AccessRole.MEMBER));
        TenantPrincipal principal = new TenantPrincipal(organization.getId(), user.getId(), user.getEmail(),
                AccessRole.MEMBER);
        return new TestTenant(organization.getId(), user.getId(), user.getEmail(), PASSWORD, signedIn(principal));
    }

    // Crea un MockMvc en el que cada petición va autenticada como ese usuario y con token CSRF.
    public MockMvc mvcFor(TestTenant tenant) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(tenant.member()))
                .build();
    }

    // Devuelve un post-procesador que autentica la petición con ese principal y agrega el token CSRF.
    public static RequestPostProcessor signedIn(TenantPrincipal principal) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.authenticated(principal,
                null, principal.authorities());
        RequestPostProcessor auth = authentication(token);
        return request -> csrf().postProcessRequest(auth.postProcessRequest(request));
    }

    public record TestTenant(UUID organizationId, UUID userId, String email, String password,
            RequestPostProcessor member) {
    }
}
