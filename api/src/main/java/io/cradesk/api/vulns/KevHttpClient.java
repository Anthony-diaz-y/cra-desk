package io.cradesk.api.vulns;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

public class KevHttpClient implements KevFeedClient {

    private final RestClient http;
    private final String url;

    // Recibe el cliente HTTP con timeouts y la URL del feed JSON de CISA.
    public KevHttpClient(RestClient http, String url) {
        this.http = http;
        this.url = url;
    }

    // Descarga el feed y convierte cada vulnerabilidad en un registro; cualquier fallo es ExternalServiceException.
    @Override
    public List<KevRecord> download() {
        JsonNode feed;
        try {
            feed = http.get().uri(url).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new ExternalServiceException("KEV: HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new ExternalServiceException("KEV: " + e.getClass().getSimpleName(), e);
        }
        if (feed == null || !feed.path("vulnerabilities").isArray()) {
            throw new ExternalServiceException("KEV: unexpected feed format");
        }
        List<KevRecord> records = new ArrayList<>();
        try {
            for (JsonNode v : feed.path("vulnerabilities")) {
                String cve = text(v, "cveID");
                LocalDate added = date(v, "dateAdded");
                if (cve != null && added != null) {
                    records.add(new KevRecord(cve, text(v, "vendorProject"), text(v, "product"),
                            text(v, "vulnerabilityName"), added, date(v, "dueDate"),
                            text(v, "knownRansomwareCampaignUse"), text(v, "shortDescription")));
                }
            }
        } catch (RuntimeException e) {
            throw new ExternalServiceException("KEV: invalid entry (" + e.getClass().getSimpleName() + ")", e);
        }
        return records;
    }

    // Devuelve el texto de un campo o null si falta o está en blanco.
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    // Devuelve la fecha AAAA-MM-DD de un campo o null si falta.
    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
