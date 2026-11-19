package pe.logyx.shared.config.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Tipos y límites de los parámetros de sistema editables por el operador. */
class ParameterKeyTest {

    @Test
    void laComisionAceptaFraccionesHastaTreintaPorCiento() {
        assertEquals("0.08", ParameterKey.COMMISSION_RATE.normalize("0.08"));
        assertEquals("0.1", ParameterKey.COMMISSION_RATE.normalize("0.100"));
        assertEquals("0.0825", ParameterKey.COMMISSION_RATE.normalize(" 0.0825 "));
        assertEquals("0", ParameterKey.COMMISSION_RATE.normalize("0"));
        assertEquals("0.3", ParameterKey.COMMISSION_RATE.normalize("0.30"));
    }

    @Test
    void laComisionRechazaLoQueNoEsUnaTasaValida() {
        for (String bad : new String[]{"0.31", "-0.01", "8", "abc", "", "0.12345", null}) {
            BusinessException e = assertThrows(BusinessException.class, () -> ParameterKey.COMMISSION_RATE.normalize(bad), String.valueOf(bad));
            assertEquals("LOGYX-PAR-001", e.code());
            assertEquals("value", e.field());
            assertEquals(400, e.status());
        }
    }

    @Test
    void lasHorasSonEnterasYDentroDeRango() {
        assertEquals("24", ParameterKey.ASSIGNMENT_SLA_HOURS.normalize("24"));
        assertEquals("0", ParameterKey.ASSIGNMENT_SLA_HOURS.normalize("0"));
        assertEquals("12", ParameterKey.INCIDENT_WARN_HOURS.normalize("12.0"));
        assertEquals(Duration.ofHours(36), ParameterKey.ASSIGNMENT_SLA_HOURS.hours("36"));
        for (String bad : new String[]{"1.5", "721", "-1", "x"}) {
            assertThrows(BusinessException.class, () -> ParameterKey.ASSIGNMENT_SLA_HOURS.normalize(bad), bad);
        }
        assertThrows(BusinessException.class, () -> ParameterKey.INCIDENT_WARN_HOURS.normalize("0"));   // mínimo 1 h
    }

    @Test
    void laClaveSeBuscaSinDistinguirMayusculas() {
        assertEquals(ParameterKey.COMMISSION_RATE, ParameterKey.require("COMMISSION_RATE"));
        assertTrue(ParameterKey.parse("nope").isEmpty());
        assertEquals(404, assertThrows(BusinessException.class, () -> ParameterKey.require("nope")).status());
    }
}
