package pe.logyx.notification.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.notification.domain.model.NotificationCategory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnsubscribeTokenTest {

    private static final byte[] KEY = "una-clave-de-prueba-de-32-caracteres!!".getBytes(StandardCharsets.UTF_8);

    @Test
    void firmaYVerificaPersonaYAlcance() {
        UUID p = UUID.randomUUID();
        String t = UnsubscribeToken.sign(p, NotificationCategory.BIDS, KEY);
        var c = UnsubscribeToken.verify(t, KEY).orElseThrow();
        assertEquals(p, c.profileId());
        assertEquals(NotificationCategory.BIDS, c.category());
        assertFalse(c.all());
        var all = UnsubscribeToken.verify(UnsubscribeToken.sign(p, null, KEY), KEY).orElseThrow();
        assertTrue(all.all());
        assertEquals("ALL", all.scope());
        assertTrue(t.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"), "apto para una URL sin escapar");
    }

    @Test
    void rechazaAlteradoOtraClaveOtraPersonaYBasura() {
        UUID p = UUID.randomUUID();
        String t = UnsubscribeToken.sign(p, NotificationCategory.BIDS, KEY);
        assertTrue(UnsubscribeToken.verify(t, "otra-clave-de-prueba-de-32-caracteres".getBytes()).isEmpty());
        // Mismo MAC con el perfil o el alcance cambiados (otra persona, otra categoría)
        String sig = t.substring(t.indexOf('.') + 1);
        String other = UnsubscribeToken.sign(UUID.randomUUID(), NotificationCategory.BIDS, KEY);
        assertTrue(UnsubscribeToken.verify(other.substring(0, other.indexOf('.')) + "." + sig, KEY).isEmpty());
        String allPayload = UnsubscribeToken.sign(p, null, KEY);
        assertTrue(UnsubscribeToken.verify(allPayload.substring(0, allPayload.indexOf('.')) + "." + sig, KEY).isEmpty());
        // Un carácter cambiado en la firma
        char last = t.charAt(t.length() - 1);
        assertTrue(UnsubscribeToken.verify(t.substring(0, t.length() - 1) + (last == 'A' ? 'B' : 'A'), KEY).isEmpty());
        // Alcance inexistente con firma válida no sirve: se firma un payload a mano con la clave
        for (String junk : new String[]{null, "", ".", "abc", "a.b.c", "!!!.???", "x".repeat(300),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]) + ".AAAA"}) {
            assertTrue(UnsubscribeToken.verify(junk, KEY).isEmpty(), String.valueOf(junk));
        }
    }
}
