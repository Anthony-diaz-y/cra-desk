package io.cradesk.api.sbom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import io.cradesk.api.catalog.Product;
import io.cradesk.api.catalog.ProductClass;
import io.cradesk.api.catalog.ProductRepository;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.ApiKeyService;
import io.cradesk.api.identity.CreateApiKeyRequest;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;
import io.cradesk.api.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "cradesk.admin.token="
        + TestTenants.ADMIN_TOKEN)
@Import({ TestcontainersConfiguration.class, TestTenants.class })
class SbomChunkedUploadIT {

    @LocalServerPort
    int port;

    @Autowired
    TestTenants tenants;

    @Autowired
    ApiKeyService apiKeys;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductVersionRepository versions;

    @Autowired
    JdbcTemplate jdbc;

    // Borra empresas, claves y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions CASCADE");
    }

    // Comprueba en HTTP real que un cuerpo enviado por partes, sin Content-Length, que pasa de 5 MB responde 413.
    @Test
    void chunkedOversizedBodyReturns413() {
        TestTenant acme = tenants.create("Acme");
        TenantPrincipal principal = new TenantPrincipal(acme.organizationId(), acme.userId(), acme.email(),
                AccessRole.MEMBER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        String key = apiKeys.create(new CreateApiKeyRequest("ci")).key();
        Product lock = products.saveAndFlush(new Product(acme.organizationId(), "Smart Lock", null,
                ProductClass.DEFAULT));
        ProductVersion version = versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null));
        byte[] body = SbomUploadApiIT.oversized();

        HttpStatusCode status = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build()
                .put().uri(SbomUploadApiIT.sbomUrl(lock.getId().toString(), version.getId().toString()))
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(out -> out.write(body))
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status.value()).isEqualTo(413);
    }
}
