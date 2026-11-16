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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.support.FakeAi.FakeDraftGenerator;
import io.cradesk.api.support.FakeExternalFeeds.FakeKevFeedClient;
import io.cradesk.api.support.FakeExternalFeeds.FakeOsvClient;
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;
import io.cradesk.api.vulns.KevCatalogService;
import io.cradesk.api.vulns.VersionScanner;

@IntegrationTest
class AiStatusApiIT {

    static final String LOG4J = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1";

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
    FakeDraftGenerator ai;

    @Autowired
    JdbcTemplate jdbc;

    TestTenant acmeTenant;
    MockMvc acme;

    // Crea la empresa Acme con su sesión de miembro.
    @BeforeEach
    void createTenant() {
        acmeTenant = tenants.create("Acme");
        acme = tenants.mvcFor(acmeTenant);
    }

    // Borra datos y lo programado en los dobles después de cada test.
    @AfterEach
    void cleanUp() {
        osv.reset();
        kevFeed.reset();
        ai.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Comprueba que informa el proveedor y cuántos borradores con IA quedan hoy, sin bajar de cero.
    @Test
    void reportsProviderAndRemaining() throws Exception {
        acme.perform(get("/api/reporting/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.provider").value("FAKE_AI"))
                .andExpect(jsonPath("$.model").value("fake-model"))
                .andExpect(jsonPath("$.dailyLimit").value(20))
                .andExpect(jsonPath("$.remainingToday").value(20));

        acme.perform(post(openEvent() + "/draft")).andExpect(status().isOk());
        acme.perform(get("/api/reporting/ai")).andExpect(jsonPath("$.remainingToday").value(19));

        jdbc.update("UPDATE ai_usage SET count = 25 WHERE organization_id = ? AND day = ?",
                acmeTenant.organizationId(), LocalDate.now(ZoneOffset.UTC));
        acme.perform(get("/api/reporting/ai")).andExpect(jsonPath("$.remainingToday").value(0));
    }

    // Comprueba que la respuesta no trae ninguna clave ni secreto.
    @Test
    void doesNotExposeSecrets() throws Exception {
        String body = acme.perform(get("/api/reporting/ai")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("apiKey").doesNotContain("\"key\"");
    }

    // Comprueba que la sesión demo también puede consultarlo.
    @Test
    void demoCanRead() throws Exception {
        TenantPrincipal demo = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(), null,
                AccessRole.DEMO);

        acme.perform(get("/api/reporting/ai").with(TestTenants.signedIn(demo))).andExpect(status().isOk());
    }

    // Abre un evento Log4Shell en "Smart Lock" 1.0.0 y devuelve la ruta de su aviso temprano.
    private String openEvent() throws Exception {
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
        UUID findingId = jdbc.queryForObject("SELECT id FROM findings WHERE organization_id = ?", UUID.class,
                acmeTenant.organizationId());
        String eventId = JsonPath.read(acme.perform(post("/api/findings/{id}/confirm", findingId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return "/api/events/" + eventId + "/reports/EARLY_WARNING";
    }
}
