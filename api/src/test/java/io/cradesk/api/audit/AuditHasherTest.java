package io.cradesk.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditHasherTest {

    static final String PREV = "0".repeat(64);
    static final Instant AT = Instant.parse("2026-11-15T10:00:00.123456Z");

    // Calcula el hash de referencia con valores fijos.
    static String base() {
        return AuditHasher.hash(PREV, 1, AT, "MEMBER:u1", "REPORT_SENT", "report", "r1", "{\"a\":1}");
    }

    // Comprueba que el mismo contenido siempre da el mismo hash de 64 caracteres hexadecimales.
    @Test
    void isDeterministic() {
        assertThat(base()).isEqualTo(base()).matches("[0-9a-f]{64}");
    }

    // Comprueba que cambiar cualquier campo cambia el hash.
    @Test
    void changesWhenAnyFieldChanges() {
        String h = base();
        assertThat(AuditHasher.hash("1".repeat(64), 1, AT, "MEMBER:u1", "REPORT_SENT", "report", "r1", "{\"a\":1}"))
                .isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 2, AT, "MEMBER:u1", "REPORT_SENT", "report", "r1", "{\"a\":1}"))
                .isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 1, AT.plusNanos(1000), "MEMBER:u1", "REPORT_SENT", "report", "r1",
                "{\"a\":1}")).isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 1, AT, "MEMBER:u2", "REPORT_SENT", "report", "r1", "{\"a\":1}"))
                .isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 1, AT, "MEMBER:u1", "REPORT_APPROVED", "report", "r1", "{\"a\":1}"))
                .isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 1, AT, "MEMBER:u1", "REPORT_SENT", "event", "r1", "{\"a\":1}"))
                .isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 1, AT, "MEMBER:u1", "REPORT_SENT", "report", "r2", "{\"a\":1}"))
                .isNotEqualTo(h);
        assertThat(AuditHasher.hash(PREV, 1, AT, "MEMBER:u1", "REPORT_SENT", "report", "r1", "{\"a\":2}"))
                .isNotEqualTo(h);
    }

    // Comprueba que los separadores dentro de los campos no permiten mover texto de un campo a otro.
    @Test
    void fieldBoundariesMatter() {
        assertThat(AuditHasher.hash(PREV, 1, AT, "a", "bc", "t", "i", "{}"))
                .isNotEqualTo(AuditHasher.hash(PREV, 1, AT, "ab", "c", "t", "i", "{}"));
    }

    // Comprueba que el orden de las claves de los detalles no cambia su forma canónica.
    @Test
    void keyOrderOfDetailsDoesNotMatter() {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("b", 2);
        one.put("a", Map.of("y", 1, "x", 2));
        one.put("c", null);
        Map<String, Object> two = new LinkedHashMap<>();
        two.put("c", null);
        two.put("a", Map.of("x", 2, "y", 1));
        two.put("b", 2);

        assertThat(AuditHasher.canonical(one)).isEqualTo(AuditHasher.canonical(two))
                .isEqualTo("{\"a\":{\"x\":2,\"y\":1},\"b\":2,\"c\":null}");
    }
}
