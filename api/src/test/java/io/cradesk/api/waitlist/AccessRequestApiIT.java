package io.cradesk.api.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
@Transactional
class AccessRequestApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    // Comprueba que una solicitud válida se acepta sin sesión.
    @Test
    void validRequestIsAccepted() throws Exception {
        request("""
                {"email":"alice@example.com","company":"Wonderland Devices","message":"We ship 3 IoT products"}
                """)
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    // Comprueba que el mismo email con otras mayúsculas o espacios responde 202 pero se guarda una sola vez.
    @Test
    void duplicateEmailIgnoringCaseIsAcceptedButStoredOnce() throws Exception {
        request("{\"email\":\"Alice@Example.com\"}").andExpect(status().isAccepted());
        request("{\"email\":\" alice@example.com \"}").andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_requests", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM access_requests", String.class))
                .isEqualTo("alice@example.com");
    }

    // Comprueba que un email no válido responde 400 con error en el campo.
    @Test
    void invalidEmailReturns400() throws Exception {
        request("{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // Comprueba que un mensaje de más de 1000 caracteres responde 400 con error en el campo.
    @Test
    void oversizedMessageReturns400() throws Exception {
        request("{\"email\":\"alice@example.com\",\"message\":\"%s\"}".formatted("m".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    // Comprueba que el admin puede listar las solicitudes de la más reciente a la más antigua.
    @Test
    void adminCanListRequests() throws Exception {
        request("{\"email\":\"first@example.com\"}").andExpect(status().isAccepted());
        request("{\"email\":\"second@example.com\",\"company\":\"Globex\"}").andExpect(status().isAccepted());

        mvc.perform(get("/api/admin/access-requests").header("X-Admin-Token", TestTenants.ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("second@example.com"))
                .andExpect(jsonPath("$[0].company").value("Globex"))
                .andExpect(jsonPath("$[1].email").value("first@example.com"));
    }

    // Comprueba que listar las solicitudes exige el token de admin.
    @Test
    void listRequiresAdminToken() throws Exception {
        mvc.perform(get("/api/admin/access-requests"))
                .andExpect(status().isUnauthorized());
    }

    // Envía una solicitud de acceso pública con token CSRF.
    private ResultActions request(String json) throws Exception {
        return mvc.perform(post("/api/access-requests").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
