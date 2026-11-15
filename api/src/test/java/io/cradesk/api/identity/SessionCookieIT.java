package io.cradesk.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;
import io.cradesk.api.support.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestTenants.class })
class SessionCookieIT {

    @LocalServerPort
    int port;

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    // Borra las empresas y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE organizations, products, product_versions CASCADE");
    }

    // Comprueba en HTTP real que la cookie de sesión lleva HttpOnly y SameSite=Lax.
    @Test
    void sessionCookieIsHttpOnlyAndSameSiteLax() {
        TestTenant acme = tenants.create("Acme");
        RestClient http = RestClient.create("http://localhost:" + port);

        ResponseEntity<Void> csrf = http.get().uri("/api/auth/csrf").retrieve().toBodilessEntity();
        String xsrf = cookieValue(csrf.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

        ResponseEntity<Void> login = http.post().uri("/api/auth/login")
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", xsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(acme.email(), acme.password()))
                .retrieve().toBodilessEntity();

        String sessionCookie = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");
    }

    // Comprueba en HTTP real que pedir el token CSRF deja una cookie XSRF-TOKEN legible por la web.
    @Test
    void csrfEndpointSetsReadableXsrfCookie() {
        ResponseEntity<Void> csrf = RestClient.create("http://localhost:" + port)
                .get().uri("/api/auth/csrf").retrieve().toBodilessEntity();

        assertThat(csrf.getStatusCode().value()).isEqualTo(204);
        String cookie = csrf.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow();
        assertThat(cookie).doesNotContain("HttpOnly");
    }

    // Extrae el valor de una cookie a partir de las cabeceras Set-Cookie.
    private static String cookieValue(List<String> setCookies, String name) {
        return setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .findFirst().orElseThrow();
    }
}
