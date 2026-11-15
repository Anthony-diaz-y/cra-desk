package io.cradesk.api.sbom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CycloneDxParser {

    public static final Set<String> SUPPORTED_SPEC_VERSIONS = Set.of("1.4", "1.5", "1.6");
    public static final int MAX_COMPONENTS = 20_000;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_ERRORS = 20;

    private static final Pattern PURL = Pattern.compile("^pkg:[A-Za-z][A-Za-z0-9.+-]*/[^/].*");

    private final JsonMapper json;

    // Recibe el mapper JSON de Jackson que se usa para leer el documento.
    public CycloneDxParser(JsonMapper json) {
        this.json = json;
    }

    // Lee un SBOM CycloneDX JSON, valida su forma y devuelve sus datos y componentes aplanados sin duplicados.
    public ParsedSbom parse(byte[] document) {
        JsonNode root = readTree(document);
        if (!"CycloneDX".equals(text(root, "bomFormat"))) {
            throw new InvalidSbomException(Map.of("bomFormat", "must be CycloneDX"));
        }
        String specVersion = text(root, "specVersion");
        if (!SUPPORTED_SPEC_VERSIONS.contains(specVersion)) {
            throw new InvalidSbomException(Map.of("specVersion", "must be one of 1.4, 1.5, 1.6"));
        }
        JsonNode components = root.path("components");
        if (!components.isMissingNode() && !components.isArray()) {
            throw new InvalidSbomException(Map.of("components", "must be an array"));
        }
        Walk walk = new Walk();
        if (components.isArray()) {
            walk.visit(components, "components", 1);
        }
        if (!walk.errors.isEmpty()) {
            throw new InvalidSbomException(walk.errors);
        }
        JsonNode version = root.path("version");
        return new ParsedSbom(specVersion, text(root, "serialNumber"), version.isInt() ? version.intValue() : null,
                List.copyOf(walk.unique.values()));
    }

    // Convierte los bytes en un árbol JSON y exige que la raíz sea un objeto.
    private JsonNode readTree(byte[] document) {
        JsonNode root;
        try {
            root = document.length == 0 ? null : json.readTree(document);
        } catch (JacksonException e) {
            root = null;
        }
        if (root == null || root.isMissingNode()) {
            throw new InvalidSbomException(Map.of("body", "is not valid JSON"));
        }
        if (!root.isObject()) {
            throw new InvalidSbomException(Map.of("body", "must be a JSON object"));
        }
        return root;
    }

    // Devuelve el texto de un campo, o null si falta, no es texto o está en blanco.
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static final class Walk {

        private final Map<String, String> errors = new LinkedHashMap<>();
        private final Map<List<String>, ParsedComponent> unique = new LinkedHashMap<>();
        private int count;

        // Recorre un arreglo de componentes y sus hijos, validando cada uno y guardando los distintos.
        void visit(JsonNode array, String path, int depth) {
            if (depth > MAX_DEPTH) {
                throw new InvalidSbomException(Map.of("components", "nested deeper than " + MAX_DEPTH + " levels"));
            }
            for (int i = 0; i < array.size(); i++) {
                count++;
                if (count > MAX_COMPONENTS) {
                    throw new InvalidSbomException(
                            Map.of("components", "too many components (max " + MAX_COMPONENTS + ")"));
                }
                JsonNode node = array.get(i);
                String at = path + "[" + i + "]";
                add(node, at);
                JsonNode children = node.path("components");
                if (children.isArray()) {
                    visit(children, at + ".components", depth + 1);
                }
            }
        }

        // Valida un componente y, si está bien, lo agrega salvo que sea un duplicado exacto.
        private void add(JsonNode node, String at) {
            String name = text(node, "name");
            String purl = text(node, "purl");
            boolean valid = true;
            if (name == null) {
                error(at + ".name", "must not be blank");
                valid = false;
            }
            if (purl != null && !PURL.matcher(purl).matches()) {
                error(at + ".purl", "is not a valid package URL");
                valid = false;
            }
            if (valid) {
                ParsedComponent component = new ParsedComponent(text(node, "bom-ref"), text(node, "type"),
                        text(node, "group"), name, text(node, "version"), purl);
                unique.putIfAbsent(Arrays.asList(purl, component.group(), name, component.version()), component);
            }
        }

        // Registra un error con su ruta, hasta el máximo de errores permitidos.
        private void error(String path, String message) {
            if (errors.size() < MAX_ERRORS) {
                errors.put(path, message);
            }
        }
    }
}
