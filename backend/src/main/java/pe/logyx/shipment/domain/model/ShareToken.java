package pe.logyx.shipment.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Token de un enlace público de seguimiento: 128 bits aleatorios ({@link SecureRandom}) en base64url sin relleno
 * (22 caracteres). En la base de datos solo va su SHA-256: quien lea la tabla no puede reconstruir el enlace, y la
 * búsqueda por hash no permite enumerar (2^128 posibilidades, además del límite por IP).
 */
public final class ShareToken {

    /** Token recién emitido: el valor (se muestra una sola vez), su hash y la pista. */
    public record Issued(String token, String hash, String hint) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{22}$");

    private ShareToken() {}

    public static Issued issue() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(token, hash(token), token.substring(token.length() - 4));
    }

    /** Descarta lo que no puede ser un token antes de tocar la base de datos. */
    public static boolean isWellFormed(String token) {
        return token != null && FORMAT.matcher(token).matches();
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
