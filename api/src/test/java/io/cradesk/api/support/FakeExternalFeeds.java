package io.cradesk.api.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.cradesk.api.vulns.ExternalServiceException;
import io.cradesk.api.vulns.KevFeedClient;
import io.cradesk.api.vulns.KevRecord;
import io.cradesk.api.vulns.OsvClient;
import io.cradesk.api.vulns.OsvHit;
import io.cradesk.api.vulns.OsvVulnerability;

@TestConfiguration(proxyBeanMethods = false)
public class FakeExternalFeeds {

    // Reemplaza el cliente real de OSV por un doble en memoria programable desde los tests.
    @Bean
    @Primary
    FakeOsvClient fakeOsvClient() {
        return new FakeOsvClient();
    }

    // Reemplaza el cliente real del feed KEV por un doble en memoria programable desde los tests.
    @Bean
    @Primary
    FakeKevFeedClient fakeKevFeedClient() {
        return new FakeKevFeedClient();
    }

    public static class FakeOsvClient implements OsvClient {

        private final Map<String, List<OsvHit>> hits = new LinkedHashMap<>();
        private final Map<String, OsvVulnerability> details = new HashMap<>();
        private final Map<String, Integer> fetches = new HashMap<>();
        private boolean failing;

        // Programa que ese purl esté afectado por esas vulnerabilidades.
        public synchronized FakeOsvClient willReturn(String purl, OsvVulnerability... vulnerabilities) {
            for (OsvVulnerability v : vulnerabilities) {
                hits.computeIfAbsent(purl, p -> new ArrayList<>()).add(new OsvHit(v.id(), v.modified()));
                details.put(v.id(), v);
            }
            return this;
        }

        // Programa que OSV falle en la próxima llamada.
        public synchronized FakeOsvClient willFail() {
            failing = true;
            return this;
        }

        // Devuelve cuántas veces se pidió el detalle de esa vulnerabilidad.
        public synchronized int fetchCount(String id) {
            return fetches.getOrDefault(id, 0);
        }

        // Borra todo lo programado y los contadores.
        public synchronized void reset() {
            hits.clear();
            details.clear();
            fetches.clear();
            failing = false;
        }

        // Devuelve las vulnerabilidades programadas para los purls consultados, o falla si así se programó.
        @Override
        public synchronized Map<String, List<OsvHit>> queryByPurl(List<String> purls) {
            if (failing) {
                throw new ExternalServiceException("OSV: HTTP 503");
            }
            Map<String, List<OsvHit>> result = new LinkedHashMap<>();
            for (String purl : purls) {
                if (hits.containsKey(purl)) {
                    result.put(purl, List.copyOf(hits.get(purl)));
                }
            }
            return result;
        }

        // Devuelve el detalle programado de la vulnerabilidad y cuenta la llamada.
        @Override
        public synchronized OsvVulnerability fetch(String id) {
            if (failing) {
                throw new ExternalServiceException("OSV: HTTP 503");
            }
            fetches.merge(id, 1, Integer::sum);
            return details.get(id);
        }
    }

    public static class FakeKevFeedClient implements KevFeedClient {

        private final List<KevRecord> records = new ArrayList<>();
        private boolean failing;

        // Programa las entradas que devolverá la próxima descarga del catálogo.
        public synchronized FakeKevFeedClient willReturn(KevRecord... entries) {
            records.clear();
            records.addAll(List.of(entries));
            failing = false;
            return this;
        }

        // Programa que la descarga del catálogo falle.
        public synchronized FakeKevFeedClient willFail() {
            failing = true;
            return this;
        }

        // Borra lo programado.
        public synchronized void reset() {
            records.clear();
            failing = false;
        }

        // Devuelve las entradas programadas o falla si así se programó.
        @Override
        public synchronized List<KevRecord> download() {
            if (failing) {
                throw new ExternalServiceException("KEV: HTTP 503");
            }
            return List.copyOf(records);
        }
    }

    // Crea una vulnerabilidad de OSV de prueba con sus alias.
    public static OsvVulnerability vuln(String id, String severity, String... aliases) {
        return new OsvVulnerability(id, "Summary of " + id, severity, List.of(aliases),
                Instant.parse("2021-12-10T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"));
    }

    // Crea una entrada del catálogo KEV de prueba.
    public static KevRecord kev(String cveId) {
        return new KevRecord(cveId, "Vendor", "Product", "Vulnerability " + cveId, LocalDate.of(2021, 12, 10),
                LocalDate.of(2021, 12, 24), "Known", "Description of " + cveId);
    }
}
