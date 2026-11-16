package io.cradesk.api.sbom;

import static io.cradesk.api.sbom.SbomUploadApiIT.createProduct;
import static io.cradesk.api.sbom.SbomUploadApiIT.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
class SbomUploadByNameApiIT {

    @Autowired
    MockMvc anonymous;

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    String productId;
    String key;

    // Crea una empresa con el producto "Smart Lock" y una clave de API para la CLI.
    @BeforeEach
    void createProductAndKey() throws Exception {
        MockMvc acme = tenants.mvcFor(tenants.create("Acme"));
        productId = createProduct(acme, "Smart Lock");
        key = newKey(acme);
    }

    // Borra empresas, claves y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions CASCADE");
    }

    // Comprueba que subir por nombre crea la versión que falta y responde 201 con sus ids.
    @Test
    void uploadByNameCreatesVersion() throws Exception {
        upload(key, "Smart Lock", "1.4.0", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.versionId").exists())
                .andExpect(jsonPath("$.componentCount").value(5))
                .andExpect(jsonPath("$.uploadedVia").value("CLI"));

        assertThat(jdbc.queryForList("SELECT version FROM product_versions", String.class)).containsExactly("1.4.0");
    }

    // Comprueba que el nombre del producto se busca sin distinguir mayúsculas ni espacios extra.
    @Test
    void nameMatchingIgnoresCaseAndSpaces() throws Exception {
        upload(key, " smart  LOCK ", "1.4.0", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId));
    }

    // Comprueba que un producto que no existe responde 404 y no se crea.
    @Test
    void unknownProductReturns404() throws Exception {
        upload(key, "Camera", "1.0.0", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product 'Camera' not found"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(1);
    }

    // Comprueba que una segunda subida a una versión existente sin crear reemplaza el SBOM.
    @Test
    void existingVersionWithoutCreateIsReplaced() throws Exception {
        upload(key, "Smart Lock", "1.4.0", null).andExpect(status().isCreated());

        upload(key, "Smart Lock", "1.4.0", "false").andExpect(status().isOk());
    }

    // Comprueba que sin permiso para crear, una versión que no existe responde 404.
    @Test
    void missingVersionWithoutCreateReturns404() throws Exception {
        upload(key, "Smart Lock", "9.9.9", "false")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Version '9.9.9' not found for product 'Smart Lock'"));
    }

    // Comprueba que nombre o versión vacíos responden 400 con error en el parámetro.
    @Test
    void blankParamsReturn400() throws Exception {
        upload(key, "  ", "1.0.0", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.product").exists());
        anonymous.perform(put("/api/sbom").param("product", "Smart Lock")
                        .header("Authorization", "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON).content(fixture("syft-alpine-1.5.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
    }

    // Comprueba que la clave de otra empresa no encuentra el producto aunque tenga el mismo nombre que busca.
    @Test
    void otherTenantsProductIsNotFound() throws Exception {
        String globexKey = newKey(tenants.mvcFor(tenants.create("Globex")));

        upload(globexKey, "Smart Lock", "1.0.0", null).andExpect(status().isNotFound());
    }

    // Comprueba que dos subidas simultáneas que crean la misma versión terminan bien con una sola versión.
    @Test
    void concurrentUploadsCreatingSameVersion() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    int code = upload(key, "Smart Lock", "2.0.0", null).andReturn().getResponse().getStatus();
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_versions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sboms", Integer.class)).isEqualTo(1);
    }

    // Sube el SBOM de ejemplo por nombre de producto y versión con una clave de API.
    private ResultActions upload(String apiKey, String product, String version, String createVersion)
            throws Exception {
        var request = put("/api/sbom").param("product", product).param("version", version)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.parseMediaType("application/vnd.cyclonedx+json"))
                .content(fixture("syft-alpine-1.5.json"));
        if (createVersion != null) {
            request.param("createVersion", createVersion);
        }
        return anonymous.perform(request);
    }

    // Crea una clave de API con la sesión indicada y devuelve su valor.
    private static String newKey(MockMvc mvc) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/api-keys").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ci\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.key");
    }
}
