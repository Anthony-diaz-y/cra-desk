package io.cradesk.api.sbom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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
import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;

@IntegrationTest
class SbomUploadApiIT {

    static final MediaType CYCLONEDX = MediaType.parseMediaType("application/vnd.cyclonedx+json");

    @Autowired
    MockMvc anonymous;

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    TestTenant acmeTenant;
    MockMvc acme;
    String productId;
    String versionId;

    // Crea una empresa con un producto y una versión donde subir el SBOM.
    @BeforeEach
    void createVersion() throws Exception {
        acmeTenant = tenants.create("Acme");
        acme = tenants.mvcFor(acmeTenant);
        productId = createProduct(acme, "Smart Lock");
        versionId = createVersion(acme, productId, "1.0.0");
    }

    // Borra empresas, claves y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions CASCADE");
    }

    // Comprueba que subir un SBOM válido responde 201 con el resumen y el hash del archivo.
    @Test
    void uploadReturns201WithSummary() throws Exception {
        byte[] file = fixture("syft-alpine-1.5.json");

        upload(acme, file, CYCLONEDX)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.versionId").value(versionId))
                .andExpect(jsonPath("$.specVersion").value("1.5"))
                .andExpect(jsonPath("$.componentCount").value(5))
                .andExpect(jsonPath("$.componentsWithoutPurl").value(1))
                .andExpect(jsonPath("$.sizeBytes").value(file.length))
                .andExpect(jsonPath("$.sha256").value(sha256(file)))
                .andExpect(jsonPath("$.uploadedVia").value("WEB"));
    }

    // Comprueba que volver a subir reemplaza el SBOM y deja solo los componentes nuevos.
    @Test
    void reuploadReplacesComponents() throws Exception {
        upload(acme, fixture("syft-alpine-1.5.json"), MediaType.APPLICATION_JSON).andExpect(status().isCreated());

        upload(acme, fixture("nested-1.6.json"), CYCLONEDX)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentCount").value(3));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sboms", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT name FROM sbom_components ORDER BY name", String.class))
                .containsExactly("linux-kernel", "lock-firmware", "openssl");
    }

    // Comprueba que un SBOM con errores responde 400 con las rutas y no guarda nada.
    @Test
    void invalidSbomReturns400WithErrors() throws Exception {
        upload(acme, fixture("bad-components.json"), CYCLONEDX)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors['components[1].name']").value("must not be blank"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sboms", Integer.class)).isZero();
    }

    // Comprueba que un SBOM guardado en UTF-16 (como lo deja PowerShell 5.1) responde 400 y no 500.
    @Test
    void utf16BodyReturns400() throws Exception {
        byte[] utf16 = new String(fixture("syft-alpine-1.5.json"), StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_16LE);

        upload(acme, utf16, MediaType.APPLICATION_JSON)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").value("must be UTF-8 encoded JSON"));
    }

    // Comprueba que un nombre de componente más largo que su columna responde 400 y no 500.
    @Test
    void overlongComponentNameReturns400() throws Exception {
        String json = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":[{\"name\":\""
                + "n".repeat(600) + "\"}]}";

        upload(acme, json.getBytes(StandardCharsets.UTF_8), CYCLONEDX)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['components[0].name']").value("must be at most 512 characters"));
    }

    // Comprueba que un SBOM en XML responde 415.
    @Test
    void xmlReturns415() throws Exception {
        upload(acme, "<bom xmlns=\"http://cyclonedx.org/schema/bom/1.5\"/>".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_XML)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // Comprueba que un cuerpo de más de 5 MB responde 413 y no guarda nada.
    @Test
    void oversizedBodyReturns413() throws Exception {
        upload(acme, oversized(), CYCLONEDX)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sboms", Integer.class)).isZero();
    }

    // Comprueba que subir con una clave de API queda registrado como CLI.
    @Test
    void cliKeyUploadIsRecordedAsCli() throws Exception {
        String key = JsonPath.read(acme.perform(post("/api/api-keys")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");

        anonymous.perform(put(sbomUrl(productId, versionId)).header("Authorization", "Bearer " + key)
                        .contentType(CYCLONEDX).content(fixture("syft-alpine-1.5.json")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadedVia").value("CLI"));
    }

    // Comprueba que la cuenta demo no puede subir SBOM.
    @Test
    void demoCannotUpload() throws Exception {
        TenantPrincipal demo = new TenantPrincipal(acmeTenant.organizationId(), acmeTenant.userId(),
                acmeTenant.email(), AccessRole.DEMO);

        anonymous.perform(put(sbomUrl(productId, versionId)).with(TestTenants.signedIn(demo))
                        .contentType(CYCLONEDX).content(fixture("syft-alpine-1.5.json")))
                .andExpect(status().isForbidden());
    }

    // Comprueba que otra empresa no puede subir el SBOM de esta versión.
    @Test
    void otherTenantsVersionReturns404() throws Exception {
        MockMvc globex = tenants.mvcFor(tenants.create("Globex"));

        upload(globex, fixture("syft-alpine-1.5.json"), CYCLONEDX).andExpect(status().isNotFound());
    }

    // Comprueba que una versión pedida bajo otro producto de la misma empresa responde 404.
    @Test
    void versionOfOtherProductReturns404() throws Exception {
        String camera = createProduct(acme, "Camera");

        acme.perform(put(sbomUrl(camera, versionId)).contentType(CYCLONEDX).content(fixture("syft-alpine-1.5.json")))
                .andExpect(status().isNotFound());
    }

    // Comprueba que dos subidas simultáneas a la misma versión terminan con un único SBOM coherente.
    @Test
    void concurrentUploadsLeaveOneConsistentSbom() throws Exception {
        byte[] first = fixture("syft-alpine-1.5.json");
        byte[] second = fixture("nested-1.6.json");
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (byte[] file : List.of(first, second)) {
            Thread thread = new Thread(() -> {
                await(start);
                try {
                    int code = upload(acme, file, CYCLONEDX).andReturn().getResponse().getStatus();
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
            thread.join(30_000);
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sboms", Integer.class)).isEqualTo(1);
        Set<String> names = Set.copyOf(jdbc.queryForList("SELECT name FROM sbom_components", String.class));
        assertThat(names).isIn(Set.of("busybox", "musl", "zlib", "libcrypto3", "ca-certificates-bundle"),
                Set.of("lock-firmware", "linux-kernel", "openssl"));
    }

    // Envía la subida del SBOM a la versión del test con ese tipo de contenido.
    private ResultActions upload(MockMvc mvc, byte[] body, MediaType type) throws Exception {
        return mvc.perform(put(sbomUrl(productId, versionId)).contentType(type).content(body));
    }

    // Arma la URL del SBOM de una versión de producto.
    static String sbomUrl(String productId, String versionId) {
        return "/api/products/" + productId + "/versions/" + versionId + "/sbom";
    }

    // Crea un producto y devuelve su id.
    static String createProduct(MockMvc mvc, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"productClass\":\"DEFAULT\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // Crea una versión del producto y devuelve su id.
    static String createVersion(MockMvc mvc, String productId, String version) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/products/{id}/versions", productId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"%s\"}".formatted(version)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // Lee un archivo de ejemplo de la carpeta de recursos de test.
    static byte[] fixture(String name) throws IOException {
        try (InputStream in = SbomUploadApiIT.class.getResourceAsStream("/sbom/" + name)) {
            return in.readAllBytes();
        }
    }

    // Genera un JSON CycloneDX válido que pasa 1 KB del límite de 5 MB.
    static byte[] oversized() {
        String head = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"padding\":\"";
        String tail = "\"}";
        int fill = 5 * 1024 * 1024 + 1024 - head.length() - tail.length();
        return (head + "x".repeat(fill) + tail).getBytes(StandardCharsets.UTF_8);
    }

    // Calcula el SHA-256 de los bytes en hexadecimal.
    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    // Espera a que el contador llegue a cero, como máximo diez segundos.
    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
