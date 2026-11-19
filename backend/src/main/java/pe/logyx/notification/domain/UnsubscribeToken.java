package pe.logyx.notification.domain;

import pe.logyx.notification.domain.model.NotificationCategory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Token de baja sin estado (V24, 2.2; RFC 8058): {@code base64url(perfil ‖ alcance) . base64url(HMAC-SHA256)} con la
 * clave {@code logyx.mail.unsubscribe-secret}. El alcance es una categoría de aviso o {@code ALL} (todo el correo de
 * avisos). No vence: el enlace de un correo viejo debe seguir funcionando; cambiar la clave invalida todos.
 * No revela el email ni la organización (solo el id del perfil, que nadie puede alterar sin la clave).
 */
public final class UnsubscribeToken {

    public static final String ALL = "ALL";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final int MAX_LENGTH = 200;

    /** {@code category == null} = baja de todo el correo de avisos. */
    public record Claims(UUID profileId, NotificationCategory category) {
        public boolean all() { return category == null; }
        public String scope() { return category == null ? ALL : category.name(); }
    }

    private UnsubscribeToken() {}

    public static String sign(UUID profileId, NotificationCategory category, byte[] secret) {
        byte[] payload = payload(profileId, category == null ? ALL : category.name());
        return ENC.encodeToString(payload) + "." + ENC.encodeToString(mac(payload, secret));
    }

    /** Vacío si el token está mal formado, la firma no coincide o el alcance no existe (misma respuesta). */
    public static Optional<Claims> verify(String token, byte[] secret) {
        if (token == null || token.isEmpty() || token.length() > MAX_LENGTH) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) return Optional.empty();
        try {
            byte[] payload = DEC.decode(token.substring(0, dot));
            byte[] given = DEC.decode(token.substring(dot + 1));
            if (payload.length <= 16 || !MessageDigest.isEqual(mac(payload, secret), given)) return Optional.empty();
            ByteBuffer b = ByteBuffer.wrap(payload);
            UUID profile = new UUID(b.getLong(), b.getLong());
            String scope = new String(payload, 16, payload.length - 16, StandardCharsets.US_ASCII);
            if (ALL.equals(scope)) return Optional.of(new Claims(profile, null));
            return Optional.of(new Claims(profile, NotificationCategory.valueOf(scope)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] payload(UUID profileId, String scope) {
        byte[] s = scope.getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(16 + s.length).putLong(profileId.getMostSignificantBits())
                .putLong(profileId.getLeastSignificantBits()).put(s).array();
    }

    private static byte[] mac(byte[] payload, byte[] secret) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return m.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }
}
