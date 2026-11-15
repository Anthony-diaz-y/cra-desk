package io.cradesk.api.vulns;

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
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.support.FakeExternalFeeds.FakeKevFeedClient;
import io.cradesk.api.support.FakeExternalFeeds.FakeOsvClient;
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;

@IntegrationTest
class VulnsApiIT {

    static final String LOG4J = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1";
    static final String LODASH = "pkg:npm/lodash@4.17.15";

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
    String productId;
    String versionId;
    String url;

    // Crea una empresa con una versión y un SBOM con log4j y lodash, y programa KEV y OSV.
    @BeforeEach
    void uploadSbom() throws Exception {
        acmeTenant = tenants.create("Acme");
        acme = tenants.mvcFor(acmeTenant);
        productId = createProduct(acme, "Smart Lock");
        versionId = createVersion(acme, productId, "1.0.0");
        url = "/api/products/" + productId + "/versions/" + versionId;
        kevFeed.willReturn(kev("CVE-2021-44228"));
        kevCatalog.refresh();
        osv.willReturn(LOG4J, vuln("GHSA-jfh8-c2jp-5v3q", "CRITICAL", "CVE-2021-44228"))
                .willReturn(LODASH, vuln("GHSA-35jh-r3h4-6jhm", "HIGH", "CVE-2021-23337"));
        String json = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":["
                + "{\"name\":\"log4j-core\",\"version\":\"2.14.1\",\"purl\":\"" + LOG4J + "\"},"
                + "{\"name\":\"lodash\",\"version\":\"4.17.15\",\"purl\":\"" + LODASH + "\"},"
                + "{\"name\":\"mcu-bootloader\",\"version\":\"0.9.2\"}]}";
        acme.perform(put(sbomUrl(productId, versionId)).contentType(MediaType.APPLICATION_JSON)
                .content(json.getBytes(StandardCharsets.UTF_8))).andExpect(status().isCreated());
    }

    // Borra datos, catálogo, vulnerabilidades y lo programado en los dobles después de cada test.
    @AfterEach
    void cleanUp() {
        osv.reset();
        kevFeed.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Comprueba que después del escaneo se ven las vulnerabilidades de la versión con la de KEV primero.
    @Test
    void versionVulnerabilitiesAfterScan() throws Exception {
        scanner.scan(UUID.fromString(versionId));

        acme.perform(get(url + "/vulnerabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.componentsScanned").value(2))
                .andExpect(jsonPath("$.componentsNotScanned").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value("GHSA-jfh8-c2jp-5v3q"))
                .andExpect(jsonPath("$.items[0].kev").value(true))
                .andExpect(jsonPath("$.items[0].kevDateAdded").value("2021-12-10"))
                .andExpect(jsonPath("$.items[0].aliases[0]").value("CVE-2021-44228"))
                .andExpect(jsonPath("$.items[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].componentName").value("log4j-core"))
                .andExpect(jsonPath("$.items[0].purl").value(LOG4J))
                .andExpect(jsonPath("$.items[1].kev").value(false));
    }

    // Comprueba que antes de escanear se ve el escaneo pendiente y la lista vacía.
    @Test
    void versionWithoutScanShowsEmptyList() throws Exception {
        acme.perform(get(url + "/vulnerabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // Comprueba que una versión sin SBOM responde 404 en las vulnerabilidades y al pedir escaneo.
    @Test
    void versionWithoutSbomReturns404() throws Exception {
        String other = createVersion(acme, productId, "2.0.0");

        acme.perform(get("/api/products/{p}/versions/{v}/vulnerabilities", productId, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No SBOM uploaded for this version"));
        acme.perform(post("/api/products/{p}/versions/{v}/scan", productId, other))
                .andExpect(status().isNotFound());
    }

    // Comprueba que pedir un escaneo responde 202 y lo deja pendiente.
    @Test
    void scanRequestReturns202AndQueues() throws Exception {
        jdbc.update("UPDATE version_scans SET status = 'DONE'");

        acme.perform(post(url + "/scan")).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT status FROM version_scans WHERE product_version_id = ?",
                String.class, UUID.fromString(versionId))).isEqualTo("PENDING");
    }

    // Comprueba que la cuenta demo puede leer pero no pedir escaneos.
    @Test
    void demoCannotRequestScan() throws Exception {
        TenantPrincipal demo = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(),
                acmeTenant.email(), AccessRole.DEMO);

        anonymous.perform(post(url + "/scan").with(TestTenants.signedIn(demo))).andExpect(status().isForbidden());
        anonymous.perform(get(url + "/vulnerabilities").with(TestTenants.signedIn(demo)))
                .andExpect(status().isOk());
    }

    // Comprueba que la lista de hallazgos muestra solo los de la propia empresa.
    @Test
    void findingsListShowsOnlyOwnOrganization() throws Exception {
        scanner.scan(UUID.fromString(versionId));
        MockMvc globex = tenants.mvcFor(tenants.create("Globex"));

        acme.perform(get("/api/findings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId))
                .andExpect(jsonPath("$[0].productName").value("Smart Lock"))
                .andExpect(jsonPath("$[0].version").value("1.0.0"))
                .andExpect(jsonPath("$[0].vulnerabilityId").value("GHSA-jfh8-c2jp-5v3q"))
                .andExpect(jsonPath("$[0].cveId").value("CVE-2021-44228"))
                .andExpect(jsonPath("$[0].kevDateAdded").value("2021-12-10"))
                .andExpect(jsonPath("$[0].present").value(true))
                .andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"));
        globex.perform(get("/api/findings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Comprueba que otra empresa no puede ver ni escanear la versión.
    @Test
    void otherTenantsVersionReturns404() throws Exception {
        MockMvc globex = tenants.mvcFor(tenants.create("Globex"));

        globex.perform(get(url + "/vulnerabilities")).andExpect(status().isNotFound());
        globex.perform(post(url + "/scan")).andExpect(status().isNotFound());
    }
}
