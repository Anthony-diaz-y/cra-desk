package io.cradesk.api.audit;

import static io.cradesk.api.sbom.SbomUploadApiIT.createProduct;
import static io.cradesk.api.sbom.SbomUploadApiIT.createVersion;
import static io.cradesk.api.sbom.SbomUploadApiIT.sbomUrl;
import static io.cradesk.api.support.FakeExternalFeeds.kev;
import static io.cradesk.api.support.FakeExternalFeeds.vuln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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
class AuditApiIT {

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
    AuditService audit;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    TestTenant acmeTenant;
    MockMvc acme;
    String early;

    // Sube un SBOM con Log4Shell, confirma el hallazgo y deja listo el aviso temprano del evento.
    @BeforeEach
    void openEvent() throws Exception {
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
        UUID findingId = jdbc.queryForObject("SELECT id FROM findings", UUID.class);
        String eventId = JsonPath.read(acme.perform(post("/api/findings/{id}/confirm", findingId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        early = "/api/events/" + eventId + "/reports/EARLY_WARNING";
    }

    // Limpia los datos, los dobles y el contexto de seguridad después de cada test.
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        osv.reset();
        kevFeed.reset();
        ai.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Genera, aprueba y envía el aviso temprano.
    void draftApproveAndSend() throws Exception {
        acme.perform(post(early + "/draft").param("ai", "false")).andExpect(status().isOk());
        acme.perform(post(early + "/approve")).andExpect(status().isOk());
        acme.perform(post(early + "/sent").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enisaReference\":\"ENISA-1\"}")).andExpect(status().isOk());
    }

    // Lee la cadena de la empresa en orden de secuencia.
    List<Map<String, Object>> chain(UUID organizationId) {
        return jdbc.queryForList("SELECT seq, action, prev_hash, hash FROM audit_entries WHERE organization_id = ?"
                + " ORDER BY seq", organizationId);
    }

    // Comprueba que cada acción queda registrada en orden y enlazada con el hash de la anterior.
    @Test
    void actionsAreRecordedInOrder() throws Exception {
        draftApproveAndSend();

        List<Map<String, Object>> entries = chain(acmeTenant.organizationId());
        assertThat(entries).extracting(e -> e.get("action")).containsExactly("SBOM_UPLOADED", "FINDING_CONFIRMED",
                "REPORT_DRAFTED", "REPORT_APPROVED", "REPORT_SENT");
        assertThat(entries).extracting(e -> ((Number) e.get("seq")).longValue()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(entries.get(0).get("prev_hash")).isEqualTo("0".repeat(64));
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i).get("prev_hash")).isEqualTo(entries.get(i - 1).get("hash"));
        }

        acme.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[0].seq").value(5))
                .andExpect(jsonPath("$.items[0].action").value("REPORT_SENT"))
                .andExpect(jsonPath("$.items[0].actorId").value(acmeTenant.userId().toString()))
                .andExpect(jsonPath("$.items[0].actorRole").value("MEMBER"))
                .andExpect(jsonPath("$.items[0].targetType").value("report"))
                .andExpect(jsonPath("$.items[0].details.enisaReference").value("ENISA-1"))
                .andExpect(jsonPath("$.items[4].action").value("SBOM_UPLOADED"));
    }

    // Comprueba que una cadena sin tocar se verifica como válida.
    @Test
    void verifyIsValid() throws Exception {
        draftApproveAndSend();

        acme.perform(get("/api/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.entries").value(5))
                .andExpect(jsonPath("$.firstInvalidSeq").doesNotExist());
    }

    // Comprueba que si alguien cambia una entrada saltándose el trigger, la verificación señala esa entrada.
    @Test
    void tamperedEntryIsDetected() throws Exception {
        draftApproveAndSend();
        jdbc.execute("ALTER TABLE audit_entries DISABLE TRIGGER audit_entries_append_only");
        try {
            jdbc.update("UPDATE audit_entries SET details = '{\"generatedBy\":\"MANUAL\"}' WHERE organization_id = ?"
                    + " AND seq = 3", acmeTenant.organizationId());
        } finally {
            jdbc.execute("ALTER TABLE audit_entries ENABLE TRIGGER audit_entries_append_only");
        }

        acme.perform(get("/api/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstInvalidSeq").value(3));
    }

    // Comprueba que borrar la última entrada también se detecta gracias a la cabeza de la cadena.
    @Test
    void deletedLastEntryIsDetected() throws Exception {
        draftApproveAndSend();
        jdbc.execute("ALTER TABLE audit_entries DISABLE TRIGGER audit_entries_append_only");
        try {
            jdbc.update("DELETE FROM audit_entries WHERE organization_id = ? AND seq = 5",
                    acmeTenant.organizationId());
        } finally {
            jdbc.execute("ALTER TABLE audit_entries ENABLE TRIGGER audit_entries_append_only");
        }

        acme.perform(get("/api/audit/verify"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstInvalidSeq").value(5));
    }

    // Comprueba que la base de datos rechaza modificar o borrar entradas de auditoría.
    @Test
    void updateAndDeleteAreRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_entries SET action = 'X'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_entries"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    // Comprueba que diez acciones simultáneas de la misma empresa dejan una cadena continua y válida.
    @Test
    void concurrentActionsKeepTheChain() throws Exception {
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        List<Integer> statuses = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            String name = "key-" + i;
            Thread t = new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    statuses.add(acme.perform(post("/api/api-keys").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\"}")).andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    statuses.add(-1);
                }
            });
            workers.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : workers) {
            t.join(30_000);
        }

        assertThat(statuses).hasSize(threads).containsOnly(201);
        assertThat(chain(acmeTenant.organizationId())).extracting(e -> ((Number) e.get("seq")).longValue())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
        acme.perform(get("/api/audit/verify"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.entries").value(12));
    }

    // Comprueba que cada empresa tiene su propia cadena y solo ve sus entradas.
    @Test
    void eachOrganizationHasItsOwnChain() throws Exception {
        TestTenant globexTenant = tenants.create("Globex");
        MockMvc globex = tenants.mvcFor(globexTenant);
        globex.perform(post("/api/api-keys").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
                .andExpect(status().isCreated());

        assertThat(chain(globexTenant.organizationId())).extracting(e -> ((Number) e.get("seq")).longValue())
                .containsExactly(1L);
        globex.perform(get("/api/audit"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].action").value("API_KEY_CREATED"))
                .andExpect(jsonPath("$.items[0].details.name").value("ci"));
        acme.perform(get("/api/audit")).andExpect(jsonPath("$.total").value(2));
        globex.perform(get("/api/audit/verify")).andExpect(jsonPath("$.valid").value(true));
        acme.perform(get("/api/audit/verify")).andExpect(jsonPath("$.valid").value(true));
    }

    // Comprueba que si la acción falla después de registrar, la transacción se deshace sin dejar la entrada.
    @Test
    void failedActionLeavesNoAuditEntry() {
        TenantPrincipal principal = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(),
                acmeTenant.email(), AccessRole.MEMBER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        int before = chain(acmeTenant.organizationId()).size();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            audit.record("REPORT_APPROVED", "report", "x", Map.of());
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        assertThat(chain(acmeTenant.organizationId())).hasSize(before);
        assertThat(jdbc.queryForObject("SELECT last_seq FROM audit_chain_heads WHERE organization_id = ?",
                Long.class, acmeTenant.organizationId())).isEqualTo(before);
    }

    // Comprueba que registrar fuera de una transacción es un error de programación.
    @Test
    void recordOutsideTransactionFails() {
        assertThatThrownBy(() -> audit.record("X", "y", "z", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // Comprueba que una clave de la CLI no puede leer la auditoría y la demo sí.
    @Test
    void cliCannotReadAuditButDemoCan() throws Exception {
        TenantPrincipal cli = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(), null,
                AccessRole.CLI);
        TenantPrincipal demo = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(), null,
                AccessRole.DEMO);

        acme.perform(get("/api/audit").with(TestTenants.signedIn(cli))).andExpect(status().isForbidden());
        acme.perform(get("/api/audit").with(TestTenants.signedIn(demo))).andExpect(status().isOk());
    }
}
