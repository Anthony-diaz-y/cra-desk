package io.cradesk.api.sbom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class CycloneDxParserTest {

    private final CycloneDxParser parser = new CycloneDxParser(JsonMapper.builder().build());

    // Comprueba que un SBOM de Syft se lee bien: sin duplicados, sin el producto raíz y contando los que no tienen purl.
    @Test
    void parsesSyftSbom() throws IOException {
        ParsedSbom sbom = parser.parse(fixture("syft-alpine-1.5.json"));

        assertThat(sbom.specVersion()).isEqualTo("1.5");
        assertThat(sbom.serialNumber()).startsWith("urn:uuid:");
        assertThat(sbom.bomVersion()).isEqualTo(1);
        assertThat(sbom.components()).hasSize(5);
        assertThat(sbom.componentsWithoutPurl()).isEqualTo(1);
        assertThat(sbom.components()).extracting(ParsedComponent::name).doesNotContain("smart-lock-firmware");
        assertThat(sbom.components()).filteredOn(c -> c.name().equals("busybox")).singleElement()
                .extracting(ParsedComponent::purl).isEqualTo("pkg:apk/alpine/busybox@1.36.1-r2");
    }

    // Comprueba que los componentes anidados aparecen en la lista plana.
    @Test
    void flattensNestedComponents() throws IOException {
        ParsedSbom sbom = parser.parse(fixture("nested-1.6.json"));

        assertThat(sbom.components()).extracting(ParsedComponent::name)
                .containsExactly("lock-firmware", "linux-kernel", "openssl");
    }

    // Comprueba que un SPDX se rechaza indicando que no es CycloneDX.
    @Test
    void rejectsSpdx() throws IOException {
        InvalidSbomException e = catchThrowableOfType(InvalidSbomException.class,
                () -> parser.parse(fixture("spdx.json")));

        assertThat(e.errors()).containsExactlyEntriesOf(Map.of("bomFormat", "must be CycloneDX"));
    }

    // Comprueba que una versión de CycloneDX anterior a 1.4 se rechaza.
    @Test
    void rejectsOldSpecVersion() throws IOException {
        InvalidSbomException e = catchThrowableOfType(InvalidSbomException.class,
                () -> parser.parse(fixture("cyclonedx-1.3.json")));

        assertThat(e.errors()).containsEntry("specVersion", "must be one of 1.4, 1.5, 1.6");
    }

    // Comprueba que se informan juntos todos los errores de componentes con su ruta.
    @Test
    void reportsAllComponentErrorsWithPaths() throws IOException {
        InvalidSbomException e = catchThrowableOfType(InvalidSbomException.class,
                () -> parser.parse(fixture("bad-components.json")));

        assertThat(e.errors())
                .containsEntry("components[1].name", "must not be blank")
                .containsEntry("components[2].purl", "is not a valid package URL")
                .hasSize(2);
    }

    // Comprueba que un texto que no es JSON, un cuerpo vacío o un arreglo se rechazan.
    @Test
    void rejectsNonJsonEmptyAndNonObject() {
        assertThat(errorsFor("not json")).containsEntry("body", "is not valid JSON");
        assertThat(errorsFor("")).containsEntry("body", "is not valid JSON");
        assertThat(errorsFor("[]")).containsEntry("body", "must be a JSON object");
    }

    // Comprueba que más de 20 000 componentes se rechazan.
    @Test
    void rejectsTooManyComponents() {
        StringBuilder json = new StringBuilder("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":[");
        for (int i = 0; i <= CycloneDxParser.MAX_COMPONENTS; i++) {
            json.append(i == 0 ? "" : ",").append("{\"name\":\"c").append(i).append("\"}");
        }
        json.append("]}");

        assertThat(errorsFor(json.toString())).containsEntry("components", "too many components (max 20000)");
    }

    // Comprueba que componentes anidados a más de 16 niveles se rechazan.
    @Test
    void rejectsDeepNesting() {
        String open = "{\"name\":\"c\",\"components\":[";
        String json = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.5\",\"components\":["
                + open.repeat(CycloneDxParser.MAX_DEPTH) + "{\"name\":\"leaf\"}" + "]}".repeat(CycloneDxParser.MAX_DEPTH)
                + "]}";

        assertThat(errorsFor(json)).containsEntry("components", "nested deeper than 16 levels");
    }

    // Comprueba que los nombres con caracteres Unicode se conservan igual.
    @Test
    void keepsUnicodeNames() {
        ParsedSbom sbom = parser.parse("""
                {"bomFormat":"CycloneDX","specVersion":"1.4","components":[{"name":"ñandú-core","version":"1.0"}]}
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(sbom.components()).extracting(ParsedComponent::name).containsExactly("ñandú-core");
    }

    // Lee un archivo de ejemplo de la carpeta de recursos de test.
    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = CycloneDxParserTest.class.getResourceAsStream("/sbom/" + name)) {
            return in.readAllBytes();
        }
    }

    // Parsea el texto esperando que falle y devuelve el mapa de errores.
    private Map<String, String> errorsFor(String json) {
        return catchThrowableOfType(InvalidSbomException.class,
                () -> parser.parse(json.getBytes(StandardCharsets.UTF_8))).errors();
    }
}
