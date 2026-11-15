package io.cradesk.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ApiKeyApiIT {

    @Autowired
    MockMvc anonymous;

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    MockMvc acme;
    MockMvc globex;

    // Crea dos empresas con su sesión de miembro.
    @BeforeEach
    void createTenants() {
        acme = tenants.mvcFor(tenants.create("Acme"));
        globex = tenants.mvcFor(tenants.create("Globex"));
    }

    // Borra claves, empresas y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions CASCADE");
    }

    // Comprueba que la clave se muestra solo al crearla y que en la base queda solo su hash.
    @Test
    void createdKeyIsShownOnceAndStoredHashed() throws Exception {
        String body = createKey(acme, "ci pipeline")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ci pipeline"))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.key");
        String prefix = JsonPath.read(body, "$.prefix");

        assertThat(key).startsWith("crd_" + prefix + "_");
        assertThat(prefix).hasSize(8);
        acme.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prefix").value(prefix))
                .andExpect(jsonPath("$[0].key").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT key_hash FROM api_keys", String.class))
                .isEqualTo(SecretTokens.sha256Hex(key));
    }

    // Comprueba que la clave autentica llamadas al catálogo sin sesión ni token CSRF.
    @Test
    void keyAuthenticatesCatalogCalls() throws Exception {
        String key = newKey(acme);

        anonymous.perform(post("/api/products").header("Authorization", "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Smart Lock","productClass":"DEFAULT"}
                                """))
                .andExpect(status().isCreated());
        anonymous.perform(get("/api/products").header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT last_used_at FROM api_keys", Object.class)).isNotNull();
    }

    // Comprueba que con una clave no se pueden crear otras claves.
    @Test
    void keyCannotManageKeys() throws Exception {
        String key = newKey(acme);

        anonymous.perform(post("/api/api-keys").header("Authorization", "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"escalation\"}"))
                .andExpect(status().isForbidden());
    }

    // Comprueba que una clave revocada ya no autentica.
    @Test
    void revokedKeyIsRejected() throws Exception {
        String body = createKey(acme, "laptop").andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.key");
        String id = JsonPath.read(body, "$.id");

        acme.perform(delete("/api/api-keys/{id}", id)).andExpect(status().isNoContent());

        anonymous.perform(get("/api/products").header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        acme.perform(get("/api/api-keys"))
                .andExpect(jsonPath("$[0].revokedAt").exists());
    }

    // Comprueba que claves mal formadas, inexistentes o con el secreto cambiado responden 401.
    @Test
    void malformedOrUnknownKeyIsRejected() throws Exception {
        String key = newKey(acme);
        String tampered = key.substring(0, key.length() - 1) + (key.endsWith("A") ? "B" : "A");
        String unknown = "crd_ZZZZZZZZ_" + SecretTokens.random(32);

        for (String candidate : new String[] { "crd_nope", unknown, tampered }) {
            anonymous.perform(get("/api/products").header("Authorization", "Bearer " + candidate))
                    .andExpect(status().isUnauthorized());
        }
    }

    // Comprueba que la clave de una empresa no ve los productos de otra.
    @Test
    void keyOfOtherTenantCannotSeeProducts() throws Exception {
        String acmeProduct = JsonPath.read(acme.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Smart Lock","productClass":"DEFAULT"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String globexKey = newKey(globex);

        anonymous.perform(get("/api/products/{id}", acmeProduct).header("Authorization", "Bearer " + globexKey))
                .andExpect(status().isNotFound());
    }

    // Comprueba que una empresa no puede revocar la clave de otra.
    @Test
    void cannotRevokeOtherTenantsKey() throws Exception {
        String acmeKeyId = JsonPath.read(createKey(acme, "acme ci").andReturn().getResponse().getContentAsString(),
                "$.id");

        globex.perform(delete("/api/api-keys/{id}", acmeKeyId))
                .andExpect(status().isNotFound());
    }

    // Envía la creación de una clave de API con ese nombre.
    private ResultActions createKey(MockMvc mvc, String name) throws Exception {
        return mvc.perform(post("/api/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)));
    }

    // Crea una clave de API y devuelve su valor completo.
    private String newKey(MockMvc mvc) throws Exception {
        return JsonPath.read(createKey(mvc, "key").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.key");
    }
}
