package io.cradesk.api.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Constructor privado para que esta clase de utilidades no se instancie.
    private SecretTokens() {
    }

    // Genera un secreto aleatorio de esa cantidad de bytes codificado en base64url sin relleno.
    public static String random(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    // Calcula el SHA-256 del texto y lo devuelve en hexadecimal.
    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    // Compara dos textos en tiempo constante para no revelar cuántos caracteres coinciden.
    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(sha256(a), sha256(b));
    }

    // Calcula el SHA-256 del texto en UTF-8.
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
