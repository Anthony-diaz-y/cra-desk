package io.cradesk.api.vulns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KevHttpClientTest {

    static final String URL = "https://kev.test/feeds/known_exploited_vulnerabilities.json";

    MockRestServiceServer server;
    KevHttpClient client;

    // Crea el cliente del catálogo KEV apuntando a un servidor simulado.
    @BeforeEach
    void createClient() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KevHttpClient(builder.build(), URL);
    }

    // Comprueba que se leen todas las entradas del feed oficial con sus fechas.
    @Test
    void parsesOfficialFeed() throws IOException {
        server.expect(requestTo(URL)).andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        List<KevRecord> records = client.download();

        assertThat(records).hasSize(3);
        KevRecord log4shell = records.get(0);
        assertThat(log4shell.cveId()).isEqualTo("CVE-2021-44228");
        assertThat(log4shell.vendorProject()).isEqualTo("Apache");
        assertThat(log4shell.product()).isEqualTo("Log4j2");
        assertThat(log4shell.dateAdded()).isEqualTo(LocalDate.of(2021, 12, 10));
        assertThat(log4shell.dueDate()).isEqualTo(LocalDate.of(2021, 12, 24));
        assertThat(log4shell.knownRansomware()).isEqualTo("Known");
    }

    // Comprueba que un error del servidor de CISA se convierte en ExternalServiceException.
    @Test
    void serverErrorBecomesExternalServiceException() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        ExternalServiceException e = catchThrowableOfType(ExternalServiceException.class, () -> client.download());

        assertThat(e).hasMessageStartingWith("KEV:");
    }

    // Lee el archivo de ejemplo del catálogo KEV.
    private static String fixture() throws IOException {
        try (InputStream in = KevHttpClientTest.class
                .getResourceAsStream("/kev/known_exploited_vulnerabilities.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
