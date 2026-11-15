package io.cradesk.api.vulns;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ OsvProperties.class, KevProperties.class, ScanProperties.class })
public class VulnsConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    // Crea el cliente de OSV con su URL base y timeouts de conexión y lectura.
    @Bean
    OsvClient osvClient(OsvProperties properties) {
        return new OsvHttpClient(externalRestClient(properties.baseUrl()));
    }

    // Crea el cliente del feed KEV de CISA con los mismos timeouts.
    @Bean
    KevFeedClient kevFeedClient(KevProperties properties) {
        return new KevHttpClient(externalRestClient(null), properties.url());
    }

    // Crea un RestClient para un servicio externo con timeouts de 5 s para conectar y 30 s para leer.
    static RestClient externalRestClient(String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(READ_TIMEOUT);
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        return baseUrl == null ? builder.build() : builder.baseUrl(baseUrl).build();
    }
}
