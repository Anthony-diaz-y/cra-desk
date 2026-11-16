package io.cradesk.api.vulns;

import static io.cradesk.api.sbom.SbomUploadApiIT.createProduct;
import static io.cradesk.api.sbom.SbomUploadApiIT.createVersion;
import static io.cradesk.api.sbom.SbomUploadApiIT.sbomUrl;
import static io.cradesk.api.support.FakeExternalFeeds.kev;
import static io.cradesk.api.support.FakeExternalFeeds.vuln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.cradesk.api.support.FakeExternalFeeds.FakeKevFeedClient;
import io.cradesk.api.support.FakeExternalFeeds.FakeOsvClient;
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;

@IntegrationTest
class VersionScannerIT {

    static final String LOG4J = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1";
    static final String LODASH = "pkg:npm/lodash@4.17.15";
    static final String LOG4SHELL = "GHSA-jfh8-c2jp-5v3q";

    @Autowired
    TestTenants tenants;

    @Autowired
    VersionScanner scanner;

    @Autowired
    ScanQueue queue;

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

    // Crea una empresa con una versión, carga KEV con Log4Shell, programa OSV y sube un SBOM con log4j y lodash.
    @BeforeEach
    void uploadSbom() throws Exception {
        acmeTenant = tenants.create("Acme");
        acme = tenants.mvcFor(acmeTenant);
        productId = createProduct(acme, "Smart Lock");
        versionId = createVersion(acme, productId, "1.0.0");
        kevFeed.willReturn(kev("CVE-2021-44228"));
        kevCatalog.refresh();
        osv.willReturn(LOG4J, vuln(LOG4SHELL, "CRITICAL", "CVE-2021-44228"))
                .willReturn(LODASH, vuln("GHSA-35jh-r3h4-6jhm", "HIGH", "CVE-2021-23337"));
        upload(true);
    }

    // Borra datos, catálogo, vulnerabilidades y lo programado en los dobles después de cada test.
    @AfterEach
    void cleanUp() {
        osv.reset();
        kevFeed.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Comprueba que subir un SBOM deja la versión con un escaneo pendiente.
    @Test
    void uploadQueuesAScan() {
        assertThat(scan().get("status")).isEqualTo("PENDING");
    }

    // Comprueba que el escaneo crea las coincidencias y un hallazgo solo para la vulnerabilidad que está en KEV.
    @Test
    void scanCreatesMatchesAndKevFinding() {
        scanner.scan(UUID.fromString(versionId));

        Map<String, Object> scan = scan();
        assertThat(scan.get("status")).isEqualTo("DONE");
        assertThat(scan.get("components_scanned")).isEqualTo(2);
        assertThat(scan.get("components_not_scanned")).isEqualTo(2);
        assertThat(count("vulnerability_matches")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vulnerability_matches WHERE kev", Integer.class))
                .isEqualTo(1);
        Map<String, Object> finding = jdbc.queryForMap("SELECT * FROM findings");
        assertThat(finding.get("cve_id")).isEqualTo("CVE-2021-44228");
        assertThat(finding.get("vulnerability_id")).isEqualTo(LOG4SHELL);
        assertThat(finding.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(finding.get("present")).isEqualTo(true);
        assertThat(finding.get("organization_id")).isEqualTo(acmeTenant.organizationId());
    }

    // Comprueba que volver a escanear no duplica el hallazgo.
    @Test
    void rescanDoesNotDuplicateFindings() {
        scanner.scan(UUID.fromString(versionId));
        scanner.scan(UUID.fromString(versionId));

        assertThat(count("findings")).isEqualTo(1);
        assertThat(count("vulnerability_matches")).isEqualTo(2);
    }

    // Comprueba que un hallazgo que ya no aparece tras corregir el SBOM se conserva marcado como ausente.
    @Test
    void findingNoLongerPresentIsKeptAsAbsent() throws Exception {
        scanner.scan(UUID.fromString(versionId));

        upload(false);
        scanner.scan(UUID.fromString(versionId));

        assertThat(count("findings")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT present FROM findings", Boolean.class)).isFalse();
        assertThat(count("vulnerability_matches")).isEqualTo(1);
    }

    // Comprueba que una vulnerabilidad sin cambios en OSV no se vuelve a descargar.
    @Test
    void unchangedVulnerabilityIsNotFetchedAgain() {
        scanner.scan(UUID.fromString(versionId));
        scanner.scan(UUID.fromString(versionId));

        assertThat(osv.fetchCount(LOG4SHELL)).isEqualTo(1);
    }

    // Comprueba que si OSV falla el escaneo queda fallido con motivo y sin datos a medias.
    @Test
    void osvFailureMarksFailedWithoutPartialData() {
        osv.willFail();

        scanner.scan(UUID.fromString(versionId));

        Map<String, Object> scan = scan();
        assertThat(scan.get("status")).isEqualTo("FAILED");
        assertThat((String) scan.get("error")).startsWith("OSV:");
        assertThat(count("vulnerability_matches")).isZero();
        assertThat(count("findings")).isZero();
    }

    // Comprueba que pedir un escaneo que ya está pendiente no cambia nada.
    @Test
    void requestWhilePendingIsANoOp() {
        Object requestedAt = scan().get("requested_at");

        queue.request(UUID.fromString(versionId));

        assertThat(scan().get("status")).isEqualTo("PENDING");
        assertThat(scan().get("requested_at")).isEqualTo(requestedAt);
    }

    // Comprueba que dos registros de OSV con el mismo CVE explotado generan un solo hallazgo.
    @Test
    void sameKevCveFromTwoOsvRecordsIsOneFinding() {
        osv.willReturn(LOG4J, vuln("GO-2021-0001", "CRITICAL", "cve-2021-44228"));

        scanner.scan(UUID.fromString(versionId));

        assertThat(count("findings")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cve_id FROM findings", String.class)).isEqualTo("CVE-2021-44228");
        assertThat(count("vulnerability_matches")).isEqualTo(3);
    }

    // Comprueba que si el SBOM se reemplaza durante la consulta a OSV no se escribe nada viejo y queda pendiente.
    @Test
    void sbomReplacedDuringScanLeavesItPendingWithoutErrors() {
        osv.onQuery(() -> {
            try {
                upload(false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        scanner.scan(UUID.fromString(versionId));

        assertThat(scan().get("status")).isEqualTo("PENDING");
        assertThat(scan().get("error")).isNull();
        assertThat(count("findings")).isZero();
        scanner.scan(UUID.fromString(versionId));
        assertThat(scan().get("status")).isEqualTo("DONE");
        assertThat(count("vulnerability_matches")).isEqualTo(1);
    }

    // Comprueba que una vulnerabilidad cuyo detalle OSV ya no tiene se salta sin fallar el escaneo.
    @Test
    void vulnerabilityWithoutDetailIsSkipped() {
        osv.willReturnWithoutDetail(LODASH, "GHSA-gone-gone-gone");

        scanner.scan(UUID.fromString(versionId));

        assertThat(scan().get("status")).isEqualTo("DONE");
        assertThat(count("vulnerability_matches")).isEqualTo(2);
    }

    // Comprueba que un purl con @ solo en el nombre (scope npm sin codificar) cuenta como no escaneado.
    @Test
    void unencodedScopeWithoutVersionIsNotScanned() throws Exception {
        String json = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":["
                + "{\"name\":\"core\",\"purl\":\"pkg:npm/@angular/core\"},"
                + "{\"name\":\"lodash\",\"version\":\"4.17.15\",\"purl\":\"" + LODASH + "\"}]}";
        acme.perform(put(sbomUrl(productId, versionId)).contentType(MediaType.APPLICATION_JSON)
                .content(json.getBytes(StandardCharsets.UTF_8))).andExpect(status().is2xxSuccessful());

        scanner.scan(UUID.fromString(versionId));

        assertThat(scan().get("components_scanned")).isEqualTo(1);
        assertThat(scan().get("components_not_scanned")).isEqualTo(1);
    }

    // Comprueba que un error interno no deja texto técnico en el motivo del fallo que ve el usuario.
    @Test
    void internalErrorIsNotExposedInScanError() {
        osv.onQuery(() -> {
            throw new IllegalStateException("ERROR: duplicate key value violates unique constraint secret_idx");
        });

        scanner.scan(UUID.fromString(versionId));

        assertThat(scan().get("status")).isEqualTo("FAILED");
        assertThat(scan().get("error")).isEqualTo("Internal error during scan");
    }

    // Sube un SBOM con lodash, un componente sin purl, uno sin versión y, si se pide, log4j.
    private void upload(boolean withLog4j) throws Exception {
        String log4j = withLog4j ? "{\"name\":\"log4j-core\",\"version\":\"2.14.1\",\"purl\":\"" + LOG4J + "\"}," : "";
        String json = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":[" + log4j
                + "{\"name\":\"lodash\",\"version\":\"4.17.15\",\"purl\":\"" + LODASH + "\"},"
                + "{\"name\":\"mcu-bootloader\",\"version\":\"0.9.2\"},"
                + "{\"name\":\"zlib\",\"purl\":\"pkg:apk/alpine/zlib\"}]}";
        acme.perform(put(sbomUrl(productId, versionId)).contentType(MediaType.APPLICATION_JSON)
                .content(json.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is2xxSuccessful());
    }

    // Devuelve la fila del escaneo de la versión del test.
    private Map<String, Object> scan() {
        return jdbc.queryForMap("SELECT * FROM version_scans WHERE product_version_id = ?",
                UUID.fromString(versionId));
    }

    // Cuenta las filas de una tabla.
    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
