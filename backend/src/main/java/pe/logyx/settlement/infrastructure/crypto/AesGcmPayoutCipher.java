package pe.logyx.settlement.infrastructure.crypto;

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.settlement.application.port.out.PayoutCipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * AES-256-GCM para los números de cuenta de cobro (2.12). Clave {@code logyx.settlement.payout-key} (base64 de 32
 * bytes, env {@code LOGYX_PAYOUT_KEY}). Formato: versión (1 byte) + IV (12) + texto cifrado con etiqueta. En dev/test
 * hay una clave fija de desarrollo; en prod, sin clave, guardar o leer un dato de cobro falla con un error claro
 * (el resto de la API sigue). Cambiar la clave vuelve ilegibles los datos guardados: rotarla exige re-cifrar.
 */
@ApplicationScoped
public class AesGcmPayoutCipher implements PayoutCipher {

    private static final byte VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "logyx.settlement.payout-key") Optional<String> configuredKey;

    private volatile SecretKeySpec key;

    private SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k != null) return k;
        String raw = configuredKey.filter(s -> !s.isBlank()).orElse(null);
        if (raw == null && LaunchMode.current() != LaunchMode.NORMAL) {
            raw = "ZGV2LW9ubHktcGF5b3V0LWtleS0zMi1ieXRlcy0hISE=";   // "dev-only-payout-key-32-bytes-!!!"
        }
        if (raw == null) {
            throw new IllegalStateException("Falta LOGYX_PAYOUT_KEY (base64 de 32 bytes) para cifrar los datos de cobro");
        }
        byte[] bytes = Base64.getDecoder().decode(raw.trim());
        if (bytes.length != 32) throw new IllegalStateException("LOGYX_PAYOUT_KEY debe ser base64 de 32 bytes");
        k = new SecretKeySpec(bytes, "AES");
        key = k;
        return k;
    }

    @Override
    public byte[] encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + out.length).put(VERSION).put(iv).put(out).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato de cobro", e);
        }
    }

    @Override
    public String decrypt(byte[] sealed) {
        try {
            ByteBuffer b = ByteBuffer.wrap(sealed);
            if (b.get() != VERSION) throw new IllegalStateException("Formato de cifrado desconocido");
            byte[] iv = new byte[IV_BYTES];
            b.get(iv);
            byte[] body = new byte[b.remaining()];
            b.get(body);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(body), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el dato de cobro (¿cambió LOGYX_PAYOUT_KEY?)", e);
        }
    }
}
