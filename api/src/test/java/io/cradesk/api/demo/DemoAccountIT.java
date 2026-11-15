package io.cradesk.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.identity.DemoProperties;
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
@Transactional
class DemoAccountIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    DemoAccountInitializer initializer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestTenants tenants;

    @PersistenceContext
    EntityManager entityManager;

    // Asegura que la cuenta demo exista aunque otro test haya vaciado las tablas.
    @BeforeEach
    void seedDemo() {
        initializer.seed();
    }

    // Comprueba que el botón de demo abre una sesión que ve los dos productos de ejemplo.
    @Test
    void demoLoginSeesSampleProducts() throws Exception {
        MockHttpSession session = demoSession();

        mvc.perform(get("/api/products").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Home Gateway"))
                .andExpect(jsonPath("$[1].name").value("Smart Door Lock"));
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.role").value("DEMO"))
                .andExpect(jsonPath("$.organizationName").value(DemoProperties.ORGANIZATION_NAME));
    }

    // Comprueba que la sesión demo no puede crear, modificar ni borrar nada.
    @Test
    void demoCannotWrite() throws Exception {
        MockHttpSession session = demoSession();
        String productId = JsonPath.read(mvc.perform(get("/api/products").session(session))
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        mvc.perform(post("/api/products").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hacked","productClass":"DEFAULT"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(put("/api/products/{id}", productId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hacked","productClass":"DEFAULT"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/{id}", productId).session(session).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/products/{id}/versions", productId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"6.6.6\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/api-keys").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo key\"}"))
                .andExpect(status().isForbidden());
    }

    // Comprueba que ejecutar el inicializador dos veces no duplica la empresa ni los productos.
    @Test
    void initializerIsIdempotent() {
        initializer.seed();

        Integer organizations = jdbc.queryForObject("SELECT count(*) FROM organizations WHERE name = ?",
                Integer.class, DemoProperties.ORGANIZATION_NAME);
        Integer products = jdbc.queryForObject("""
                SELECT count(*) FROM products p JOIN organizations o ON o.id = p.organization_id WHERE o.name = ?
                """, Integer.class, DemoProperties.ORGANIZATION_NAME);
        assertThat(organizations).isEqualTo(1);
        assertThat(products).isEqualTo(2);
    }

    // Comprueba que al usuario demo no se puede entrar con contraseña.
    @Test
    void demoUserCannotPasswordLogin() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"any-password-at-all"}
                                """.formatted(DemoProperties.EMAIL)))
                .andExpect(status().isUnauthorized());
    }

    // Comprueba que una empresa cualquiera con el nombre de la demo no impide crear la cuenta demo.
    @Test
    void organizationWithDemoNameDoesNotBlockSeeding() throws Exception {
        entityManager.flush();
        jdbc.update("DELETE FROM product_versions WHERE product_id IN (SELECT p.id FROM products p"
                + " JOIN organizations o ON o.id = p.organization_id WHERE o.name = ?)", DemoProperties.ORGANIZATION_NAME);
        jdbc.update("DELETE FROM products WHERE organization_id IN (SELECT id FROM organizations WHERE name = ?)",
                DemoProperties.ORGANIZATION_NAME);
        jdbc.update("DELETE FROM users WHERE organization_id IN (SELECT id FROM organizations WHERE name = ?)",
                DemoProperties.ORGANIZATION_NAME);
        jdbc.update("DELETE FROM organizations WHERE name = ?", DemoProperties.ORGANIZATION_NAME);
        tenants.create(DemoProperties.ORGANIZATION_NAME);

        initializer.seed();
        initializer.seed();

        demoSession();
    }

    // Abre la sesión demo y la devuelve.
    private MockHttpSession demoSession() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/demo").with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }
}
