package io.cradesk.api.vulns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsvHttpClientTest {

    static final String BASE = "https://osv.test";

    MockRestServiceServer server;
    OsvHttpClient client;

    // Crea el cliente de OSV apuntando a un servidor simulado.
    @BeforeEach
    void createClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OsvHttpClient(builder.build());
    }

    // Comprueba que 2001 purls se consultan en tres lotes de como máximo 1000.
    @Test
    void queriesInBatchesOf1000() {
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/v1/querybatch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    int queries = body.split("\"purl\"", -1).length - 1;
                    assertThat(queries).isLessThanOrEqualTo(OsvHttpClient.BATCH_SIZE);
                    String results = String.join(",", Collections.nCopies(queries, "{}"));
                    return withSuccess("{\"results\":[" + results + "]}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
        List<String> purls = IntStream.range(0, 2001).mapToObj(i -> "pkg:npm/p" + i + "@1.0.0").toList();

        Map<String, List<OsvHit>> hits = client.queryByPurl(purls);

        assertThat(hits).isEmpty();
        server.verify();
    }

    // Comprueba que una consulta con next_page_token se repite con el token y se juntan todos los ids.
    @Test
    void followsNextPageToken() throws IOException {
        server.expect(requestTo(BASE + "/v1/querybatch"))
                .andRespond(withSuccess(fixture("querybatch-response.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v1/querybatch"))
                .andExpect(content().string(containsString("\"page_token\":\"page-2-token\"")))
                .andRespond(withSuccess(fixture("querybatch-page2.json"), MediaType.APPLICATION_JSON));

        Map<String, List<OsvHit>> hits = client.queryByPurl(List.of(
                "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1", "pkg:npm/left-pad@1.3.0",
                "pkg:npm/lodash@4.17.15"));

        assertThat(hits.get("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"))
                .extracting(OsvHit::id).containsExactly("GHSA-jfh8-c2jp-5v3q", "GHSA-7rjr-3q55-vv33");
        assertThat(hits.get("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1").get(0).modified())
                .isEqualTo(Instant.parse("2024-03-21T17:56:15.123456Z"));
        assertThat(hits).doesNotContainKey("pkg:npm/left-pad@1.3.0");
        assertThat(hits.get("pkg:npm/lodash@4.17.15"))
                .extracting(OsvHit::id).containsExactly("GHSA-p6mc-m468-83gw", "GHSA-35jh-r3h4-6jhm");
        server.verify();
    }

    // Comprueba que el detalle de una vulnerabilidad trae resumen, severidad y alias CVE.
    @Test
    void parsesVulnerabilityDetail() throws IOException {
        server.expect(requestTo(BASE + "/v1/vulns/GHSA-jfh8-c2jp-5v3q"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture("vuln-GHSA-jfh8-c2jp-5v3q.json"), MediaType.APPLICATION_JSON));

        OsvVulnerability vulnerability = client.fetch("GHSA-jfh8-c2jp-5v3q");

        assertThat(vulnerability.id()).isEqualTo("GHSA-jfh8-c2jp-5v3q");
        assertThat(vulnerability.summary()).isEqualTo("Remote code injection in Log4j");
        assertThat(vulnerability.severity()).isEqualTo("CRITICAL");
        assertThat(vulnerability.aliases()).containsExactly("CVE-2021-44228");
        assertThat(vulnerability.published()).isEqualTo(Instant.parse("2021-12-10T00:40:56Z"));
    }

    // Comprueba que sin etiqueta de severidad se usa el vector CVSS.
    @Test
    void fallsBackToCvssVector() throws IOException {
        server.expect(requestTo(BASE + "/v1/vulns/OSV-2026-0001"))
                .andRespond(withSuccess(fixture("vuln-with-cvss-only.json"), MediaType.APPLICATION_JSON));

        OsvVulnerability vulnerability = client.fetch("OSV-2026-0001");

        assertThat(vulnerability.severity()).isEqualTo("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H");
        assertThat(vulnerability.published()).isNull();
    }

    // Comprueba que un error 500 de OSV se convierte en ExternalServiceException.
    @Test
    void serverErrorBecomesExternalServiceException() {
        server.expect(requestTo(BASE + "/v1/querybatch")).andRespond(withServerError());

        ExternalServiceException e = catchThrowableOfType(ExternalServiceException.class,
                () -> client.queryByPurl(List.of("pkg:npm/lodash@4.17.15")));

        assertThat(e).hasMessageStartingWith("OSV:");
    }

    // Comprueba que una respuesta que no es JSON válido se convierte en ExternalServiceException.
    @Test
    void invalidJsonBecomesExternalServiceException() {
        server.expect(requestTo(BASE + "/v1/vulns/GHSA-x"))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.APPLICATION_JSON));

        ExternalServiceException e = catchThrowableOfType(ExternalServiceException.class,
                () -> client.fetch("GHSA-x"));

        assertThat(e).hasMessageStartingWith("OSV:");
    }

    // Lee un archivo de ejemplo de respuestas de OSV.
    private static String fixture(String name) throws IOException {
        try (InputStream in = OsvHttpClientTest.class.getResourceAsStream("/osv/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
