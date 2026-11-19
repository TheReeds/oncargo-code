package pe.logyx.settlement.infrastructure;

import org.junit.jupiter.api.Test;
import pe.logyx.settlement.infrastructure.crypto.AesGcmPayoutCipher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmPayoutCipherTest {

    @Test
    void cifraConIvAleatorioYDescifra() throws Exception {
        var c = new AesGcmPayoutCipher();
        var f = AesGcmPayoutCipher.class.getDeclaredField("configuredKey");
        f.setAccessible(true);
        f.set(c, Optional.of("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        byte[] a = c.encrypt("00219300123456789012");
        byte[] b = c.encrypt("00219300123456789012");
        assertFalse(java.util.Arrays.equals(a, b), "IV distinto en cada cifrado");
        assertFalse(new String(a, java.nio.charset.StandardCharsets.ISO_8859_1).contains("0021930012"));
        assertEquals("00219300123456789012", c.decrypt(a));
        a[a.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> c.decrypt(a), "GCM detecta la alteración");
    }
}
