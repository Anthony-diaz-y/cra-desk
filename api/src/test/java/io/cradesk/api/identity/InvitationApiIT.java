package io.cradesk.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
class InvitationApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    // Borra invitaciones, empresas y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE invitations, organizations, products, product_versions CASCADE");
    }

    // Comprueba que el admin crea una invitación y que en la base solo queda el hash del código.
    @Test
    void adminCreatesInvitationAndCodeIsStoredHashed() throws Exception {
        String code = invite("alice@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        code = JsonPath.read(code, "$.code");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM invitations");
        assertThat(row.get("code_hash")).isEqualTo(SecretTokens.sha256Hex(code));
        for (Object value : row.values()) {
            assertThat(String.valueOf(value)).doesNotContain(code);
        }
    }

    // Comprueba que sin token de admin o con un token incorrecto se responde 401.
    @Test
    void adminEndpointsRejectMissingOrWrongToken() throws Exception {
        mvc.perform(post("/api/admin/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/invitations")
                        .header("X-Admin-Token", "wrong-token-wrong-token-wrong-token-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    // Comprueba que aceptar la invitación crea la empresa, el usuario y abre la sesión.
    @Test
    void acceptingCreatesOrganizationUserAndSession() throws Exception {
        String code = codeFor("alice@example.com");

        MvcResult result = accept(code, "alice@example.com", "Wonderland Devices")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.organizationName").value("Wonderland Devices"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Wonderland Devices"));
    }

    // Comprueba que una invitación no se puede usar dos veces.
    @Test
    void invitationIsSingleUse() throws Exception {
        String code = codeFor("alice@example.com");
        accept(code, "alice@example.com", "Wonderland Devices").andExpect(status().isCreated());

        accept(code, "alice@example.com", "Another Company")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invitation is invalid or expired"));
    }

    // Comprueba que una invitación vencida se rechaza.
    @Test
    void expiredInvitationIsRejected() throws Exception {
        String code = codeFor("alice@example.com");
        jdbc.update("UPDATE invitations SET expires_at = now() - interval '1 minute'");

        accept(code, "alice@example.com", "Wonderland Devices")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invitation is invalid or expired"));
    }

    // Comprueba que el email debe coincidir con la invitación, sin importar mayúsculas ni espacios.
    @Test
    void emailMustMatchInvitationIgnoringCase() throws Exception {
        String code = codeFor("alice@example.com");

        accept(code, "bob@example.com", "Wonderland Devices")
                .andExpect(status().isBadRequest());
        accept(code, " ALICE@example.com ", "Wonderland Devices")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    // Comprueba que invitar un email que ya tiene cuenta responde 409.
    @Test
    void inviteForRegisteredEmailConflicts() throws Exception {
        tenants.create("Acme");

        invite(" ACME@example.com ").andExpect(status().isConflict());
    }

    // Comprueba que una contraseña de más de 72 bytes en UTF-8 se rechaza con error en el campo.
    @Test
    void passwordOver72BytesIsRejected() throws Exception {
        String code = codeFor("alice@example.com");

        mvc.perform(post("/api/auth/accept-invitation").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","email":"alice@example.com","password":"%s","organizationName":"Wonderland"}
                                """.formatted(code, "ñ".repeat(37))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // Envía la creación de una invitación con el token de admin.
    private ResultActions invite(String email) throws Exception {
        return mvc.perform(post("/api/admin/invitations")
                .header("X-Admin-Token", TestTenants.ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    // Crea una invitación y devuelve su código.
    private String codeFor(String email) throws Exception {
        String body = invite(email).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    // Envía la aceptación de una invitación con una contraseña válida.
    private ResultActions accept(String code, String email, String organizationName) throws Exception {
        return mvc.perform(post("/api/auth/accept-invitation").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","email":"%s","password":"a-long-enough-password","organizationName":"%s"}
                        """.formatted(code, email, organizationName)));
    }
}
