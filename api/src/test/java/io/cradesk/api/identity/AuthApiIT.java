package io.cradesk.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;

@IntegrationTest
class AuthApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    TestTenant acme;

    // Crea la empresa y el usuario con el que se inicia sesión.
    @BeforeEach
    void createTenant() {
        acme = tenants.create("Acme");
    }

    // Borra las empresas y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE organizations, products, product_versions CASCADE");
    }

    // Comprueba que con la contraseña correcta se abre una sesión que identifica al usuario.
    @Test
    void loginWithCorrectPasswordOpensSession() throws Exception {
        MockHttpSession session = login(acme.email(), acme.password());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("acme@example.com"))
                .andExpect(jsonPath("$.organizationName").value("Acme"))
                .andExpect(jsonPath("$.organizationId").value(acme.organizationId().toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    // Comprueba que el email del login se compara sin espacios ni mayúsculas.
    @Test
    void loginNormalizesEmail() throws Exception {
        MockHttpSession session = login(" Acme@Example.com ", acme.password());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("acme@example.com"));
    }

    // Comprueba que la contraseña incorrecta y el email desconocido responden exactamente igual.
    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalResponse() throws Exception {
        String wrongPassword = postLogin(acme.email(), "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = postLogin("nobody@example.com", "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    // Comprueba que tras cinco fallos ni la contraseña correcta permite entrar.
    @Test
    void fiveFailuresLockTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            postLogin(acme.email(), "wrong-password-123").andExpect(status().isUnauthorized());
        }

        postLogin(acme.email(), acme.password()).andExpect(status().isUnauthorized());
    }

    // Comprueba que pedir el usuario actual sin sesión responde 401 en formato problem details.
    @Test
    void meWithoutSessionReturns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    // Comprueba que el catálogo exige estar autenticado.
    @Test
    void catalogRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    // Comprueba que un POST con sesión pero sin token CSRF responde 403.
    @Test
    void postWithoutCsrfTokenReturns403() throws Exception {
        MockHttpSession session = login(acme.email(), acme.password());

        mvc.perform(post("/api/products").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Smart Lock","productClass":"DEFAULT"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // Comprueba que después de cerrar sesión la misma sesión ya no sirve.
    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = login(acme.email(), acme.password());

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // Envía el login con token CSRF y devuelve el resultado.
    private ResultActions postLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    // Inicia sesión con éxito y devuelve la sesión creada.
    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) postLogin(email, password)
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }
}
