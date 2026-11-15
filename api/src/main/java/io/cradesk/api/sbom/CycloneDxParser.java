package io.cradesk.api.sbom;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CycloneDxParser {

    public static final Set<String> SUPPORTED_SPEC_VERSIONS = Set.of("1.4", "1.5", "1.6");
    public static final int MAX_COMPONENTS = 20_000;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_ERRORS = 20;
    public static final Map<String, Integer> MAX_LENGTHS = Map.of("serialNumber", 100, "bom-ref", 1024, "type", 32,
            "group", 512, "name", 512, "version", 256, "purl", 2048);

    private static final Pattern PURL = Pattern.compile("^pkg:[A-Za-z][A-Za-z0-9.+-]*/[^/].*");
    private static final Set<String> COMPONENT_FIELDS = Set.of("bom-ref", "type", "group", "name", "version", "purl");

    private final JsonMapper json;

    // Recibe el mapper JSON de Jackson que crea el lector en modo streaming.
    public CycloneDxParser(JsonMapper json) {
        this.json = json;
    }

    // Lee un SBOM CycloneDX JSON en modo streaming, valida su forma y devuelve sus datos y componentes sin duplicados.
    public ParsedSbom parse(byte[] document) {
        requireUtf8(document);
        Map<String, String> top = new HashMap<>();
        Integer bomVersion = null;
        boolean componentsNotArray = false;
        Walk walk = new Walk();
        try (JsonParser parser = json.createParser(document)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                throw invalidBody("is not valid JSON");
            }
            if (first != JsonToken.START_OBJECT) {
                throw invalidBody("must be a JSON object");
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (field) {
                    case "bomFormat", "specVersion", "serialNumber" -> top.put(field, readText(parser, value));
                    case "version" -> bomVersion = readInt(parser, value);
                    case "components" -> {
                        if (value == JsonToken.START_ARRAY) {
                            walk.visit(parser, "components", 1);
                        } else {
                            componentsNotArray = true;
                            parser.skipChildren();
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw invalidBody("is not valid JSON");
            }
        } catch (JacksonException e) {
            throw invalidBody("is not valid JSON");
        }
        if (!"CycloneDX".equals(top.get("bomFormat"))) {
            throw new InvalidSbomException(Map.of("bomFormat", "must be CycloneDX"));
        }
        if (!SUPPORTED_SPEC_VERSIONS.contains(top.get("specVersion"))) {
            throw new InvalidSbomException(Map.of("specVersion", "must be one of 1.4, 1.5, 1.6"));
        }
        if (componentsNotArray) {
            throw new InvalidSbomException(Map.of("components", "must be an array"));
        }
        String serialNumber = top.get("serialNumber");
        walk.checkValue("serialNumber", "serialNumber", serialNumber);
        if (!walk.errors.isEmpty()) {
            throw new InvalidSbomException(walk.errors);
        }
        return new ParsedSbom(top.get("specVersion"), serialNumber, bomVersion, walk.distinctInDocumentOrder());
    }

    // Exige que el documento sea UTF-8 válido y sin bytes NUL, como pasa con los archivos guardados en UTF-16.
    private static void requireUtf8(byte[] document) {
        if (document.length == 0) {
            throw invalidBody("is not valid JSON");
        }
        for (byte b : document) {
            if (b == 0) {
                throw invalidBody("must be UTF-8 encoded JSON");
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(document));
        } catch (CharacterCodingException e) {
            throw invalidBody("must be UTF-8 encoded JSON");
        }
    }

    // Lee el valor actual como texto, o null si no es texto o está en blanco; salta objetos y arreglos.
    private static String readText(JsonParser parser, JsonToken value) {
        if (value == JsonToken.VALUE_STRING) {
            String text = parser.getString();
            return text.isBlank() ? null : text;
        }
        parser.skipChildren();
        return null;
    }

    // Lee el valor actual como entero, o null si no es un entero que quepa en int.
    private static Integer readInt(JsonParser parser, JsonToken value) {
        if (value == JsonToken.VALUE_NUMBER_INT && parser.getNumberType() == JsonParser.NumberType.INT) {
            return parser.getIntValue();
        }
        parser.skipChildren();
        return null;
    }

    // Crea el error de cuerpo no válido con el motivo indicado.
    private static InvalidSbomException invalidBody(String reason) {
        return new InvalidSbomException(Map.of("body", reason));
    }

    private static final class Walk {

        private final Map<String, String> errors = new LinkedHashMap<>();
        private final List<ParsedComponent> slots = new ArrayList<>();
        private int count;

        // Recorre un arreglo de componentes y sus hijos, que empieza en el token actual, sin cargarlo entero.
        void visit(JsonParser parser, String path, int depth) {
            if (depth > MAX_DEPTH) {
                throw new InvalidSbomException(Map.of("components", "nested deeper than " + MAX_DEPTH + " levels"));
            }
            int index = 0;
            for (JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
                count++;
                if (count > MAX_COMPONENTS) {
                    throw new InvalidSbomException(
                            Map.of("components", "too many components (max " + MAX_COMPONENTS + ")"));
                }
                String at = path + "[" + index++ + "]";
                if (token == JsonToken.START_OBJECT) {
                    readComponent(parser, at, depth);
                } else {
                    parser.skipChildren();
                    error(at + ".name", "must not be blank");
                }
            }
        }

        // Lee los campos de un componente, recorre sus hijos y lo guarda en su lugar del documento si es válido.
        private void readComponent(JsonParser parser, String at, int depth) {
            int slot = slots.size();
            slots.add(null);
            Map<String, String> fields = new HashMap<>();
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (COMPONENT_FIELDS.contains(field)) {
                    fields.put(field, readText(parser, value));
                } else if (field.equals("components") && value == JsonToken.START_ARRAY) {
                    visit(parser, at + ".components", depth + 1);
                } else {
                    parser.skipChildren();
                }
            }
            slots.set(slot, toComponent(fields, at));
        }

        // Devuelve los componentes válidos en el orden del documento, sin duplicados exactos.
        List<ParsedComponent> distinctInDocumentOrder() {
            Map<List<String>, ParsedComponent> unique = new LinkedHashMap<>();
            for (ParsedComponent c : slots) {
                if (c != null) {
                    unique.putIfAbsent(Arrays.asList(c.purl(), c.group(), c.name(), c.version()), c);
                }
            }
            return List.copyOf(unique.values());
        }

        // Valida un componente ya leído y lo devuelve, o devuelve null si tiene errores.
        private ParsedComponent toComponent(Map<String, String> fields, String at) {
            String name = fields.get("name");
            String purl = fields.get("purl");
            boolean valid = true;
            if (name == null) {
                error(at + ".name", "must not be blank");
                valid = false;
            }
            if (purl != null && !PURL.matcher(purl).matches()) {
                error(at + ".purl", "is not a valid package URL");
                valid = false;
            }
            for (String field : COMPONENT_FIELDS) {
                valid &= checkValue(at + "." + field, field, fields.get(field));
            }
            if (!valid) {
                return null;
            }
            return new ParsedComponent(fields.get("bom-ref"), fields.get("type"), fields.get("group"), name,
                    fields.get("version"), purl);
        }

        // Comprueba que un valor quepa en su columna y no tenga NUL; registra el error y devuelve si es válido.
        boolean checkValue(String path, String field, String value) {
            if (value == null) {
                return true;
            }
            if (value.indexOf('\u0000') >= 0) {
                error(path, "must not contain NUL characters");
                return false;
            }
            int max = MAX_LENGTHS.get(field);
            if (value.length() > max) {
                error(path, "must be at most " + max + " characters");
                return false;
            }
            return true;
        }

        // Registra un error con su ruta, hasta el máximo de errores permitidos.
        private void error(String path, String message) {
            if (errors.size() < MAX_ERRORS) {
                errors.putIfAbsent(path, message);
            }
        }
    }
}
