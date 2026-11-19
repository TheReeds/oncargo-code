package pe.logyx.shared.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeruPhoneTest {

    @Test
    void normalizaALosNueveDigitos() {
        assertEquals("987654321", PeruPhone.normalize("987654321", "p"));
        assertEquals("987654321", PeruPhone.normalize("+51 987 654 321", "p"));
        assertEquals("987654321", PeruPhone.normalize("51-987-654-321", "p"));
        assertEquals("987654321", PeruPhone.normalize("(987) 654.321", "p"));
    }

    @Test
    void vacioEsNulo() {
        assertNull(PeruPhone.normalize(null, "p"));
        assertNull(PeruPhone.normalize("   ", "p"));
    }

    @Test
    void rechazaLoDemasConElCampo() {
        for (String bad : new String[]{"12345", "98765432", "9876543210", "abc987654321", "+52 987654321"}) {
            var e = assertThrows(BusinessException.class, () -> PeruPhone.normalize(bad, "pickupContactPhone"), bad);
            assertEquals("LOGYX-VAL-002", e.code());
            assertEquals("pickupContactPhone", e.field());
        }
    }
}
