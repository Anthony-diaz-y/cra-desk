package io.cradesk.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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

import java.util.List;

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

        String products = mvc.perform(get("/api/products").session(session))
                .andReturn().getResponse().getContentAsString();
        String lockId = JsonPath.<List<String>>read(products, "$[?(@.name == 'Smart Door Lock')].id").get(0);
        String versions = mvc.perform(get("/api/products/{id}/versions", lockId).session(session))
                .andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.<List<String>>read(versions, "$[?(@.version == '1.1.0')].id").get(0);
        mvc.perform(get("/api/products/{id}/versions/{versionId}/sbom", lockId, versionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentCount").value(greaterThan(0)));
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

    // Comprueba que la demo trae un evento Log4Shell con el aviso temprano enviado a tiempo y la auditoría válida.
    @Test
    void demoHasLog4ShellEventWithEarlyWarningSent() throws Exception {
        MockHttpSession session = demoSession();

        String events = mvc.perform(get("/api/events").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cveId").value("CVE-2021-44228"))
                .andExpect(jsonPath("$[0].productName").value("Smart Door Lock"))
                .andExpect(jsonPath("$[0].version").value("1.1.0"))
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(events, "$[0].id");
        String reports = "/api/events/" + eventId + "/reports/";

        mvc.perform(get(reports + "EARLY_WARNING").session(session))
                .andExpect(jsonPath("$.draftStatus").value("APPROVED"))
                .andExpect(jsonPath("$.status").value("SENT_ON_TIME"))
                .andExpect(jsonPath("$.enisaReference").value("ENISA-DEMO-0001"))
                .andExpect(jsonPath("$.content.summary").exists());
        mvc.perform(get(reports + "NOTIFICATION").session(session))
                .andExpect(jsonPath("$.draftStatus").value("DRAFTED"))
                .andExpect(jsonPath("$.content.vulnerabilityDescription").exists());
        mvc.perform(get(reports + "FINAL_REPORT").session(session))
                .andExpect(jsonPath("$.draftStatus").value("DRAFTED"))
                .andExpect(jsonPath("$.content.lessonsLearned").exists());
        mvc.perform(get(reports + "EARLY_WARNING/export").param("format", "markdown").session(session))
                .andExpect(status().isOk());
        mvc.perform(get("/api/audit/verify").session(session))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.entries").value(greaterThan(5)));
        mvc.perform(post(reports + "NOTIFICATION/approve").session(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // Comprueba que ejecutar el inicializador dos veces no duplica la empresa ni los productos.
    @Test
    void initializerIsIdempotent() {
        initializer.seed();
        entityManager.flush();

        Integer organizations = jdbc.queryForObject("SELECT count(*) FROM organizations WHERE name = ?",
                Integer.class, DemoProperties.ORGANIZATION_NAME);
        Integer products = jdbc.queryForObject("""
                SELECT count(*) FROM products p JOIN organizations o ON o.id = p.organization_id WHERE o.name = ?
                """, Integer.class, DemoProperties.ORGANIZATION_NAME);
        assertThat(organizations).isEqualTo(1);
        assertThat(products).isEqualTo(2);
        Integer events = jdbc.queryForObject("""
                SELECT count(*) FROM reportable_events e JOIN organizations o ON o.id = e.organization_id
                WHERE o.name = ?
                """, Integer.class, DemoProperties.ORGANIZATION_NAME);
        assertThat(events).isEqualTo(1);
    }

    // Comprueba que una demo creada antes del módulo SBOM recibe el SBOM de ejemplo al volver a sembrar.
    @Test
    void existingDemoWithoutSbomGetsTheSample() {
        entityManager.flush();
        jdbc.update("DELETE FROM sboms WHERE product_version_id IN (SELECT v.id FROM product_versions v"
                + " JOIN products p ON p.id = v.product_id JOIN organizations o ON o.id = p.organization_id"
                + " WHERE o.name = ?)", DemoProperties.ORGANIZATION_NAME);
        entityManager.clear();

        initializer.seed();
        initializer.seed();
        entityManager.flush();

        Integer sboms = jdbc.queryForObject("""
                SELECT count(*) FROM sboms s JOIN product_versions v ON v.id = s.product_version_id
                JOIN products p ON p.id = v.product_id JOIN organizations o ON o.id = p.organization_id
                WHERE o.name = ? AND p.name = 'Smart Door Lock' AND v.version = '1.1.0'
                """, Integer.class, DemoProperties.ORGANIZATION_NAME);
        assertThat(sboms).isEqualTo(1);
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
        jdbc.update("UPDATE users SET email = 'retired-demo@example.com' WHERE email = ?", DemoProperties.EMAIL);
        jdbc.update("UPDATE organizations SET name = 'Retired demo' WHERE name = ?", DemoProperties.ORGANIZATION_NAME);
        entityManager.clear();
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
