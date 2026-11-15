package io.cradesk.api.vulns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

public class OsvHttpClient implements OsvClient {

    static final int BATCH_SIZE = 1000;
    static final int MAX_PAGES = 50;

    private final RestClient http;

    // Recibe el cliente HTTP ya configurado con la URL base de OSV y los timeouts.
    public OsvHttpClient(RestClient http) {
        this.http = http;
    }

    // Consulta los purls en lotes de 1000 y sigue los next_page_token hasta tener todos los ids.
    @Override
    public Map<String, List<OsvHit>> queryByPurl(List<String> purls) {
        Map<String, List<OsvHit>> hits = new LinkedHashMap<>();
        List<String> distinct = purls.stream().distinct().toList();
        for (int start = 0; start < distinct.size(); start += BATCH_SIZE) {
            List<Query> pending = distinct.subList(start, Math.min(start + BATCH_SIZE, distinct.size())).stream()
                    .map(purl -> new Query(purl, null)).toList();
            for (int page = 0; !pending.isEmpty(); page++) {
                if (page >= MAX_PAGES) {
                    throw new ExternalServiceException("OSV: too many result pages");
                }
                pending = queryBatch(pending, hits);
            }
        }
        return hits;
    }

    // Descarga el detalle de una vulnerabilidad y extrae resumen, severidad, alias y fechas.
    @Override
    public OsvVulnerability fetch(String id) {
        JsonNode vuln = call(() -> http.get().uri("/v1/vulns/{id}", id).retrieve().body(JsonNode.class));
        List<String> aliases = new ArrayList<>();
        vuln.path("aliases").forEach(alias -> aliases.add(alias.asString()));
        return new OsvVulnerability(vuln.path("id").asString(id), text(vuln, "summary"), severity(vuln),
                aliases, instant(vuln, "published"), required(instant(vuln, "modified"), "modified"));
    }

    // Envía un lote de consultas, guarda los ids encontrados y devuelve las consultas que tienen otra página.
    private List<Query> queryBatch(List<Query> queries, Map<String, List<OsvHit>> hits) {
        List<Map<String, Object>> body = queries.stream().map(Query::toJson).toList();
        JsonNode response = call(() -> http.post().uri("/v1/querybatch").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("queries", body)).retrieve().body(JsonNode.class));
        JsonNode results = response.path("results");
        if (!results.isArray() || results.size() != queries.size()) {
            throw new ExternalServiceException("OSV: unexpected querybatch response");
        }
        List<Query> nextPages = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            JsonNode result = results.get(i);
            String purl = queries.get(i).purl();
            for (JsonNode vuln : result.path("vulns")) {
                hits.computeIfAbsent(purl, p -> new ArrayList<>()).add(new OsvHit(
                        required(text(vuln, "id"), "id"), required(instant(vuln, "modified"), "modified")));
            }
            String token = text(result, "next_page_token");
            if (token != null) {
                nextPages.add(new Query(purl, token));
            }
        }
        return nextPages;
    }

    // Ejecuta una llamada a OSV y convierte cualquier fallo de red, HTTP o JSON en ExternalServiceException.
    private static JsonNode call(Supplier<JsonNode> request) {
        try {
            JsonNode node = request.get();
            if (node == null || !node.isObject()) {
                throw new ExternalServiceException("OSV: empty or non-object response");
            }
            return node;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ExternalServiceException("OSV: HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new ExternalServiceException("OSV: " + e.getClass().getSimpleName(), e);
        }
    }

    // Devuelve la etiqueta de severidad de OSV o, si no existe, el primer vector CVSS.
    private static String severity(JsonNode vuln) {
        String label = text(vuln.path("database_specific"), "severity");
        if (label != null) {
            return label;
        }
        for (JsonNode entry : vuln.path("severity")) {
            String score = text(entry, "score");
            if (score != null) {
                return score;
            }
        }
        return null;
    }

    // Devuelve el texto de un campo o null si falta o no es texto.
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    // Devuelve la fecha ISO-8601 de un campo o null si falta.
    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : Instant.parse(value);
    }

    // Exige que un dato obligatorio de la respuesta exista.
    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new ExternalServiceException("OSV: response without " + field);
        }
        return value;
    }

    private record Query(String purl, String pageToken) {

        // Convierte la consulta al JSON que espera querybatch.
        Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("package", Map.of("purl", purl));
            if (pageToken != null) {
                json.put("page_token", pageToken);
            }
            return json;
        }
    }
}
