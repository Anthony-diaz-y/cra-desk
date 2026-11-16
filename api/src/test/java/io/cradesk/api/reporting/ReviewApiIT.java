package io.cradesk.api.reporting;

import static io.cradesk.api.sbom.SbomUploadApiIT.createProduct;
import static io.cradesk.api.sbom.SbomUploadApiIT.createVersion;
import static io.cradesk.api.sbom.SbomUploadApiIT.sbomUrl;
import static io.cradesk.api.support.FakeExternalFeeds.kev;
import static io.cradesk.api.support.FakeExternalFeeds.vuln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.support.FakeExternalFeeds.FakeKevFeedClient;
import io.cradesk.api.support.FakeExternalFeeds.FakeOsvClient;
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;
import io.cradesk.api.vulns.KevCatalogService;
import io.cradesk.api.vulns.VersionScanner;

@IntegrationTest
class ReviewApiIT {

    static final String LOG4J = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1";

    @Autowired
    MockMvc anonymous;

    @Autowired
    TestTenants tenants;

    @Autowired
    VersionScanner scanner;

    @Autowired
    KevCatalogService kevCatalog;

    @Autowired
    FakeOsvClient osv;

    @Autowired
    FakeKevFeedClient kevFeed;

    @Autowired
    JdbcTemplate jdbc;

    TestTenant acmeTenant;
    MockMvc acme;
    String findingId;

    // Crea una empresa con una versión cuyo SBOM tiene log4j y la escanea para tener un hallazgo KEV pendiente.
    @BeforeEach
    void createFinding() throws Exception {
        acmeTenant = tenants.create("Acme");
        acme = tenants.mvcFor(acmeTenant);
        String productId = createProduct(acme, "Smart Lock");
        String versionId = createVersion(acme, productId, "1.0.0");
        kevFeed.willReturn(kev("CVE-2021-44228"));
        kevCatalog.refresh();
        osv.willReturn(LOG4J, vuln("GHSA-jfh8-c2jp-5v3q", "CRITICAL", "CVE-2021-44228"));
        String json = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":["
                + "{\"name\":\"log4j-core\",\"version\":\"2.14.1\",\"purl\":\"" + LOG4J + "\"}]}";
        acme.perform(put(sbomUrl(productId, versionId)).contentType(MediaType.APPLICATION_JSON)
                .content(json.getBytes(StandardCharsets.UTF_8))).andExpect(status().isCreated());
        scanner.scan(UUID.fromString(versionId));
        findingId = jdbc.queryForObject("SELECT id FROM findings", UUID.class).toString();
    }

    // Borra datos, catálogo, vulnerabilidades y lo programado en los dobles después de cada test.
    @AfterEach
    void cleanUp() {
        osv.reset();
        kevFeed.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Comprueba que confirmar abre un evento con los tres relojes del CRA a 24 h, 72 h y 14 días.
    @Test
    void confirmOpensEventWithThreeClocks() throws Exception {
        String body = confirm(acme)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.findingId").value(findingId))
                .andExpect(jsonPath("$.productName").value("Smart Lock"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.cveId").value("CVE-2021-44228"))
                .andExpect(jsonPath("$.reports.length()").value(3))
                .andExpect(jsonPath("$.reports[0].type").value("EARLY_WARNING"))
                .andExpect(jsonPath("$.reports[0].status").value("PENDING"))
                .andExpect(jsonPath("$.reports[0].draftStatus").value("EMPTY"))
                .andReturn().getResponse().getContentAsString();

        Instant confirmedAt = Instant.parse(JsonPath.read(body, "$.confirmedAt"));
        assertThat(Instant.parse((String) JsonPath.read(body, "$.reports[0].dueAt")))
                .isEqualTo(confirmedAt.plus(Duration.ofHours(24)));
        assertThat(Instant.parse((String) JsonPath.read(body, "$.reports[1].dueAt")))
                .isEqualTo(confirmedAt.plus(Duration.ofHours(72)));
        assertThat(Instant.parse((String) JsonPath.read(body, "$.reports[2].dueAt")))
                .isEqualTo(confirmedAt.plus(Duration.ofDays(14)));
        assertThat(jdbc.queryForObject("SELECT status FROM findings", String.class)).isEqualTo("CONFIRMED");
    }

    // Comprueba que descartar exige un motivo de al menos 10 caracteres.
    @Test
    void dismissNeedsAReason() throws Exception {
        dismiss(acme, "{\"reason\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        dismiss(acme, "{}").andExpect(status().isBadRequest());
    }

    // Comprueba que el descarte guarda el motivo y el hallazgo sale de la lista de pendientes.
    @Test
    void dismissStoresReasonAndHidesFromPending() throws Exception {
        dismiss(acme, "{\"reason\":\"log4j-core is only in the build tooling, not in the shipped firmware\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        acme.perform(get("/api/findings")).andExpect(jsonPath("$.length()").value(0));
        acme.perform(get("/api/findings").param("status", "DISMISSED"))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT dismiss_reason FROM findings", String.class))
                .startsWith("log4j-core is only");
    }

    // Comprueba que un hallazgo ya revisado no se puede confirmar ni descartar otra vez.
    @Test
    void reviewingTwiceConflicts() throws Exception {
        confirm(acme).andExpect(status().isCreated());

        confirm(acme).andExpect(status().isConflict());
        dismiss(acme, "{\"reason\":\"changed my mind about this one\"}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reportable_events", Integer.class)).isEqualTo(1);
    }

    // Comprueba que otra empresa no puede revisar el hallazgo ni ver el evento.
    @Test
    void otherTenantsFindingIsNotFound() throws Exception {
        String eventId = JsonPath.read(confirm(acme).andReturn().getResponse().getContentAsString(), "$.id");
        MockMvc globex = tenants.mvcFor(tenants.create("Globex"));

        confirm(globex).andExpect(status().isNotFound());
        globex.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
        globex.perform(get("/api/events")).andExpect(jsonPath("$.length()").value(0));
    }

    // Comprueba que la cuenta demo no puede confirmar hallazgos.
    @Test
    void demoCannotReview() throws Exception {
        TenantPrincipal demo = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(),
                acmeTenant.email(), AccessRole.DEMO);

        anonymous.perform(post("/api/findings/{id}/confirm", findingId).with(TestTenants.signedIn(demo)))
                .andExpect(status().isForbidden());
    }

    // Comprueba que una clave de API (una máquina) no puede confirmar: lo decide una persona.
    @Test
    void cliKeyCannotReview() throws Exception {
        String key = JsonPath.read(acme.perform(post("/api/api-keys").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ci\"}")).andReturn().getResponse().getContentAsString(), "$.key");

        anonymous.perform(post("/api/findings/{id}/confirm", findingId).header("Authorization", "Bearer " + key))
                .andExpect(status().isForbidden());
    }

    // Comprueba que la lista de eventos calcula el estado de cada reloj con la hora actual.
    @Test
    void eventsListShowsClockStatus() throws Exception {
        confirm(acme).andExpect(status().isCreated());
        jdbc.update("UPDATE reportable_events SET confirmed_at = now() - interval '23 hours'");
        jdbc.update("UPDATE reports SET due_at = now() + interval '1 hour' WHERE type = 'EARLY_WARNING'");

        acme.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reports[0].status").value("DUE_SOON"))
                .andExpect(jsonPath("$[0].reports[1].status").value("PENDING"));
    }

    // Envía la confirmación del hallazgo del test con esa sesión.
    private ResultActions confirm(MockMvc mvc) throws Exception {
        return mvc.perform(post("/api/findings/{id}/confirm", findingId));
    }

    // Envía el descarte del hallazgo del test con ese cuerpo JSON.
    private ResultActions dismiss(MockMvc mvc, String json) throws Exception {
        return mvc.perform(post("/api/findings/{id}/dismiss", findingId).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
