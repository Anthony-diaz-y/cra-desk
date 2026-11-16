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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import io.cradesk.api.support.FakeAi.FakeDraftGenerator;
import io.cradesk.api.support.FakeExternalFeeds.FakeKevFeedClient;
import io.cradesk.api.support.FakeExternalFeeds.FakeOsvClient;
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;
import io.cradesk.api.vulns.KevCatalogService;
import io.cradesk.api.vulns.VersionScanner;

@IntegrationTest
class ReportApiIT {

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
    FakeDraftGenerator ai;

    @Autowired
    JdbcTemplate jdbc;

    TestTenant acmeTenant;
    MockMvc acme;
    String eventId;
    String early;

    // Crea un hallazgo Log4Shell en "Smart Lock" 1.0.0 y lo confirma para tener un evento con tres reportes.
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
        eventId = JsonPath.read(acme.perform(post("/api/findings/{id}/confirm", findingId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        early = "/api/events/" + eventId + "/reports/EARLY_WARNING";
    }

    // Borra datos, catálogo y lo programado en los dobles después de cada test.
    @AfterEach
    void cleanUp() {
        osv.reset();
        kevFeed.reset();
        ai.reset();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions, vulnerabilities, kev_entries"
                + " CASCADE");
    }

    // Comprueba que pedir el borrador con la plantilla no llama a la IA ni consume cupo.
    @Test
    void draftWithTemplateDoesNotUseQuota() throws Exception {
        acme.perform(post(early + "/draft").param("ai", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("TEMPLATE"))
                .andExpect(jsonPath("$.draftStatus").value("DRAFTED"))
                .andExpect(jsonPath("$.content.summary").value(org.hamcrest.Matchers.containsString("CVE-2021-44228")));

        assertThat(ai.calls()).isZero();
        assertThat(usedToday()).isZero();
    }

    // Comprueba que un borrador con IA usa el proveedor y consume una unidad del cupo diario.
    @Test
    void aiDraftConsumesQuota() throws Exception {
        acme.perform(post(early + "/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("FAKE_AI"))
                .andExpect(jsonPath("$.content.summary").value(org.hamcrest.Matchers.startsWith("AI: ")))
                .andExpect(jsonPath("$.aiError").doesNotExist());

        assertThat(usedToday()).isEqualTo(1);
    }

    // Comprueba que si la IA falla se entrega el borrador de la plantilla con el motivo, no un error.
    @Test
    void aiFailureFallsBackToTemplateWithReason() throws Exception {
        ai.willFail("AI provider: HTTP 401");

        acme.perform(post(early + "/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedBy").value("TEMPLATE"))
                .andExpect(jsonPath("$.aiError").value("AI provider: HTTP 401"));
    }

    // Comprueba que al agotar el cupo diario se responde 429.
    @Test
    void dailyLimitReturns429() throws Exception {
        setUsedToday(20);

        acme.perform(post(early + "/draft"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Daily AI draft limit reached (20)"));
        assertThat(ai.calls()).isZero();
    }

    // Comprueba que con peticiones simultáneas solo pasan las que caben en el cupo restante.
    @Test
    void concurrentRequestsRespectTheLimit() throws Exception {
        setUsedToday(18);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    int code = acme.perform(post(early + "/draft")).andReturn().getResponse().getStatus();
                    synchronized (statuses) {
                        statuses.add(code);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(60_000);
        }

        assertThat(statuses).hasSize(20);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(2);
        assertThat(statuses.stream().filter(s -> s == 429).count()).isEqualTo(18);
        assertThat(usedToday()).isEqualTo(20);
    }

    // Comprueba que editar un borrador aprobado lo vuelve a dejar sin aprobar.
    @Test
    void editRevertsApprovalToDrafted() throws Exception {
        acme.perform(post(early + "/draft").param("ai", "false")).andExpect(status().isOk());
        acme.perform(post(early + "/approve")).andExpect(jsonPath("$.draftStatus").value("APPROVED"));

        acme.perform(put(early).contentType(MediaType.APPLICATION_JSON).content(validContent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftStatus").value("DRAFTED"))
                .andExpect(jsonPath("$.content.immediateMeasures").value("Disable JNDI lookups in log4j"));
    }

    // Comprueba que un contenido sin los campos del tipo se rechaza con errores por campo.
    @Test
    void invalidContentReturns400() throws Exception {
        acme.perform(put(early).contentType(MediaType.APPLICATION_JSON).content("{\"content\":{\"summary\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.summary").value("must be text"))
                .andExpect(jsonPath("$.errors.immediateMeasures").value("is required"));
    }

    // Comprueba que no se puede marcar enviado un reporte sin aprobar.
    @Test
    void sentRequiresApproval() throws Exception {
        acme.perform(post(early + "/draft").param("ai", "false")).andExpect(status().isOk());

        sent(early, "{}").andExpect(status().isConflict());
    }

    // Comprueba que marcar enviado detiene el reloj y distingue entre a tiempo y tarde.
    @Test
    void sentStopsTheClockOnTimeAndLate() throws Exception {
        approved(early);
        sent(early, "{\"enisaReference\":\"ENISA-2026-0042\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT_ON_TIME"))
                .andExpect(jsonPath("$.enisaReference").value("ENISA-2026-0042"));

        String notification = "/api/events/" + eventId + "/reports/NOTIFICATION";
        approved(notification);
        jdbc.update("UPDATE reports SET due_at = now() - interval '1 hour' WHERE type = 'NOTIFICATION'");
        sent(notification, "{}").andExpect(jsonPath("$.status").value("SENT_LATE"));
    }

    // Comprueba que una fecha de envío en el futuro se rechaza.
    @Test
    void futureSentAtReturns400() throws Exception {
        approved(early);
        String future = Instant.now().plus(2, ChronoUnit.HOURS).toString();

        sent(early, "{\"sentAt\":\"" + future + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sentAt").exists());
    }

    // Comprueba que un reporte enviado ya no se puede volver a enviar ni redactar.
    @Test
    void sentIsFinal() throws Exception {
        approved(early);
        sent(early, "{}").andExpect(status().isOk());

        sent(early, "{}").andExpect(status().isConflict());
        acme.perform(post(early + "/draft").param("ai", "false")).andExpect(status().isConflict());
        acme.perform(put(early).contentType(MediaType.APPLICATION_JSON).content(validContent()))
                .andExpect(status().isConflict());
    }

    // Comprueba que un reporte aprobado se exporta en JSON y en Markdown con un nombre de archivo limpio.
    @Test
    void exportJsonAndMarkdown() throws Exception {
        acme.perform(get(early + "/export")).andExpect(status().isConflict());
        approved(early);

        acme.perform(get(early + "/export").param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"smart-lock-1.0.0-cve-2021-44228-early_warning.json\""))
                .andExpect(jsonPath("$.cveId").value("CVE-2021-44228"))
                .andExpect(jsonPath("$.type").value("EARLY_WARNING"))
                .andExpect(jsonPath("$.fields.summary").exists());
        acme.perform(get(early + "/export").param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# Early warning")));
    }

    // Comprueba que otra empresa no puede ver ni tocar los reportes del evento.
    @Test
    void otherTenantsReportIsNotFound() throws Exception {
        MockMvc globex = tenants.mvcFor(tenants.create("Globex"));

        globex.perform(get(early)).andExpect(status().isNotFound());
        globex.perform(post(early + "/draft").param("ai", "false")).andExpect(status().isNotFound());
        globex.perform(post(early + "/approve")).andExpect(status().isNotFound());
    }

    // Comprueba que la cuenta demo puede leer el reporte pero no redactarlo ni aprobarlo.
    @Test
    void demoCannotDraftOrApprove() throws Exception {
        TenantPrincipal demo = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(),
                acmeTenant.email(), AccessRole.DEMO);

        anonymous.perform(get(early).with(TestTenants.signedIn(demo))).andExpect(status().isOk());
        anonymous.perform(post(early + "/draft").with(TestTenants.signedIn(demo))).andExpect(status().isForbidden());
        anonymous.perform(post(early + "/approve").with(TestTenants.signedIn(demo))).andExpect(status().isForbidden());
    }

    // Redacta con la plantilla y aprueba el reporte de esa ruta.
    private void approved(String report) throws Exception {
        acme.perform(post(report + "/draft").param("ai", "false")).andExpect(status().isOk());
        acme.perform(post(report + "/approve")).andExpect(status().isOk());
    }

    // Marca enviado el reporte de esa ruta con ese cuerpo JSON.
    private ResultActions sent(String report, String json) throws Exception {
        return acme.perform(post(report + "/sent").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // Devuelve un contenido válido de aviso temprano envuelto como lo espera el PUT.
    private static String validContent() {
        return """
                {"content":{"summary":"Log4Shell in Smart Lock 1.0.0","activelyExploited":"Yes",
                 "suspectedMaliciousAct":"Unknown","memberStatesAffected":"DE, FR",
                 "immediateMeasures":"Disable JNDI lookups in log4j"}}
                """;
    }

    // Devuelve cuántos borradores con IA usó hoy la empresa del test.
    private int usedToday() {
        return jdbc.queryForObject("SELECT coalesce(sum(count), 0) FROM ai_usage WHERE organization_id = ?",
                Integer.class, acmeTenant.organizationId());
    }

    // Fija cuántos borradores con IA lleva hoy (UTC) la empresa del test.
    private void setUsedToday(int used) {
        jdbc.update("INSERT INTO ai_usage (organization_id, day, count) VALUES (?, ?, ?)",
                acmeTenant.organizationId(), LocalDate.now(ZoneOffset.UTC), used);
    }
}
