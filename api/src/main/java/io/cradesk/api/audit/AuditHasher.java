package io.cradesk.api.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.json.JsonMapper;

public final class AuditHasher {

    public static final String GENESIS = "0".repeat(64);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Constructor privado para que esta clase de utilidades no se instancie.
    private AuditHasher() {
    }

    // Calcula el SHA-256 en hexadecimal de la entrada enlazada con el hash anterior.
    public static String hash(String prevHash, long seq, Instant at, String actor, String action, String targetType,
            String targetId, String canonicalDetails) {
        String payload = JSON.writeValueAsString(Arrays.asList(prevHash, Long.toString(seq), at.toString(), actor,
                action, targetType, targetId, canonicalDetails));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Convierte los detalles en JSON con las claves ordenadas, en todos los niveles.
    public static String canonical(Map<String, ?> details) {
        return JSON.writeValueAsString(sorted(details));
    }

    // Devuelve una copia del valor con los mapas ordenados por clave.
    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> result = new TreeMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), sorted(v)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(v -> result.add(sorted(v)));
            return result;
        }
        return value;
    }
}
