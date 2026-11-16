package io.cradesk.api.vulns;

import static io.cradesk.api.sbom.SbomUploadApiIT.createProduct;
import static io.cradesk.api.sbom.SbomUploadApiIT.createVersion;
import static io.cradesk.api.sbom.SbomUploadApiIT.fixture;
import static io.cradesk.api.sbom.SbomUploadApiIT.sbomUrl;
import static io.cradesk.api.support.FakeExternalFeeds.kev;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

@IntegrationTest
class ScanSchedulerIT {

    @Autowired
    TestTenants tenants;

    @Autowired
    ScanScheduler scheduler;

    @Autowired
    FakeOsvClient osv;

    @Autowired
    FakeKevFeedClient kevFeed;

    @Autowired
    JdbcTemplate jdbc;

    String withSbom;
    String otherWithSbom;
    String withoutSbom;

    // Crea un producto con dos versiones con SBOM y una sin SBOM.
    @BeforeEach
    void createVersions() throws Exception {
        MockMvc acme = tenants.mvcFor(tenants.create("Acme"));
        String productId = createProduct(acme, "Smart Lock");
        withSbom = createVersion(acme, productId, "1.0.0");
        otherWithSbom = createVersion(acme, productId, "1.1.0");
        withoutSbom = createVersion(acme, productId, "2.0.0");
        for (String version : List.of(withSbom, otherWithSbom)) {
            acme.perform(put(sbomUrl(productId, version)).contentType(MediaType.APPLICATION_JSON)
                    .content(fixture("syft-alpine-1.5.json"))).andExpect(status().isCreated());
        }
    }

    // Borra datos, catálogo y lo programado en los dobles después de cada test.
    @AfterEach
    void cleanUp() {
        osv.reset();
        kevFeed.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Comprueba que la pasada diaria actualiza KEV y vuelve a encolar todas las versiones que tienen SBOM.
    @Test
    void dailyRefreshesKevAndQueuesEveryVersionWithSbom() {
        jdbc.update("UPDATE version_scans SET status = 'DONE'");
        kevFeed.willReturn(kev("CVE-2021-44228"));

        scheduler.runDaily();

        assertThat(jdbc.queryForList("SELECT cve_id FROM kev_entries", String.class)).containsExactly("CVE-2021-44228");
        assertThat(scanStatus(withSbom)).isEqualTo("PENDING");
        assertThat(scanStatus(otherWithSbom)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM version_scans WHERE product_version_id = ?",
                Integer.class, UUID.fromString(withoutSbom))).isZero();
    }

    // Comprueba que si KEV falla la pasada diaria igual encola los escaneos.
    @Test
    void kevFailureDoesNotStopTheDailyQueue() {
        jdbc.update("UPDATE version_scans SET status = 'DONE'");
        kevFeed.willFail();

        scheduler.runDaily();

        assertThat(scanStatus(withSbom)).isEqualTo("PENDING");
    }

    // Comprueba que procesar la cola escanea todos los pendientes.
    @Test
    void processQueueDrainsPendingScans() {
        int scanned = scheduler.drain();

        assertThat(scanned).isGreaterThanOrEqualTo(2);
        assertThat(scanStatus(withSbom)).isEqualTo("DONE");
        assertThat(scanStatus(otherWithSbom)).isEqualTo("DONE");
    }

    // Comprueba que al arrancar se encolan las versiones con SBOM que nunca se escanearon, sin tocar las demás.
    @Test
    void startupQueuesVersionsNeverScanned() {
        jdbc.update("DELETE FROM version_scans WHERE product_version_id = ?", UUID.fromString(withSbom));
        jdbc.update("UPDATE version_scans SET status = 'DONE' WHERE product_version_id = ?",
                UUID.fromString(otherWithSbom));

        scheduler.queueNeverScanned();

        assertThat(scanStatus(withSbom)).isEqualTo("PENDING");
        assertThat(scanStatus(otherWithSbom)).isEqualTo("DONE");
    }

    // Comprueba que con el planificador desactivado (como en los tests) la tarea programada no escanea nada.
    @Test
    void disabledSchedulerDoesNothing() {
        scheduler.processQueue();

        assertThat(scanStatus(withSbom)).isEqualTo("PENDING");
    }

    // Devuelve el estado del escaneo de una versión.
    private String scanStatus(String versionId) {
        return jdbc.queryForObject("SELECT status FROM version_scans WHERE product_version_id = ?", String.class,
                UUID.fromString(versionId));
    }
}
