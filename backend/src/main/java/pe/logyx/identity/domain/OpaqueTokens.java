package pe.logyx.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos de un solo uso (restablecer contraseña, invitación, refresh): 32 bytes de {@link SecureRandom} en
 * base64url sin relleno (43 caracteres). En la BD solo se guarda el SHA-256 en hex: quien lea la tabla no puede
 * usarlos. Como el token tiene 256 bits de entropía no hace falta un hash lento (no es una contraseña).
 */
public final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private OpaqueTokens() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    /** SHA-256 en hex (64 caracteres) del token en claro. */
    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Forma válida de un token emitido aquí (descarta basura antes de ir a la BD). */
    public static boolean wellFormed(String raw) {
        return raw != null && raw.length() == 43 && raw.chars().allMatch(c ->
                (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_');
    }
}
