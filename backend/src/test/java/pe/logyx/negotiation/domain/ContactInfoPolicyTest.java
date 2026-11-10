package pe.logyx.negotiation.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class ContactInfoPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"987654321", "llama al 987 654 321", "987-654-321", "987.654.321", "+51 987654321",
            "+51-987-654-321", "51987654321", "9 8 7 6 5 4 3 2 1", "ventas@sur.com", "a.b+c@mail.co",
            "https://logyx.pe", "http://x.y/z", "www.fletes.pe", "transportesur.com.pe", "wa.me/51987",
            "bit.ly/abc", "@transportes_sur"})
    void bloqueaDatosDeContacto(String text) {
        assertTrue(ContactInfoPolicy.containsContactInfo(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Recojo el 2026-11-15 a las 06:00", "S/ 1,850.00 incluye IGV", "Son 12 pallets de 500 kg",
            "Placa ASA-101, guía 001-0004567", "RUC 20601234565 verificado", "Llego en 48 h", "a las 6 a. m.",
            "Total 1850.50 soles", "Jr. San Román 456, Juliaca", "OK, gracias"})
    void permiteTextoNormal(String text) {
        assertFalse(ContactInfoPolicy.containsContactInfo(text), text);
    }

    @Test
    void lanzaNeg001ConElCampo() {
        var e = assertThrows(BusinessException.class,
                () -> ContactInfoPolicy.ensureNoContactInfo("cel 987654321", "body"));
        assertEquals("LOGYX-NEG-001", e.code());
        assertEquals("body", e.field());
        assertEquals(400, e.status());
        assertDoesNotThrow(() -> ContactInfoPolicy.ensureNoContactInfo(null, "message"));
    }
}
