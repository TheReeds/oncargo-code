package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.trip.domain.model.TripStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.trip.domain.model.TripStatus.*;

class TripStatusPolicyTest {

    @Test
    void transicionesPermitidas() {
        assertTrue(TripStatusPolicy.canTransition(OPEN, FILLING));
        assertTrue(TripStatusPolicy.canTransition(OPEN, CLOSED));
        assertTrue(TripStatusPolicy.canTransition(FILLING, CLOSED));
        assertTrue(TripStatusPolicy.canTransition(FILLING, OPEN));
        assertTrue(TripStatusPolicy.canTransition(CLOSED, COMPLETED));
        for (TripStatus s : new TripStatus[]{OPEN, FILLING, CLOSED}) {
            assertTrue(TripStatusPolicy.canTransition(s, CANCELLED), s + " → CANCELLED");
        }
    }

    @Test
    void completadoYCanceladoSonTerminales() {
        for (TripStatus to : TripStatus.values()) {
            assertFalse(TripStatusPolicy.canTransition(COMPLETED, to));
            assertFalse(TripStatusPolicy.canTransition(CANCELLED, to));
        }
        assertFalse(TripStatusPolicy.canTransition(OPEN, COMPLETED));
        assertFalse(TripStatusPolicy.canTransition(CLOSED, OPEN));
    }

    @Test
    void transicionInvalidaLanzaTrp005() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> TripStatusPolicy.ensureTransition(CANCELLED, CLOSED));
        assertEquals("LOGYX-TRP-005", e.code());
        assertEquals(409, e.status());
    }

    @Test
    void soloOpenYFillingAceptanReservas() {
        assertTrue(TripStatusPolicy.acceptsBookings(OPEN));
        assertTrue(TripStatusPolicy.acceptsBookings(FILLING));
        assertFalse(TripStatusPolicy.acceptsBookings(CLOSED));
        assertFalse(TripStatusPolicy.acceptsBookings(CANCELLED));
    }

    @Test
    void primeraConfirmacionPasaAFillingYLlenoCierra() {
        assertEquals(FILLING, TripStatusPolicy.afterConfirmation(OPEN, new BigDecimal("100")));
        assertEquals(FILLING, TripStatusPolicy.afterConfirmation(FILLING, new BigDecimal("100")));
        assertEquals(CLOSED, TripStatusPolicy.afterConfirmation(OPEN, BigDecimal.ZERO));
        assertEquals(CLOSED, TripStatusPolicy.afterConfirmation(FILLING, BigDecimal.ZERO));
    }

    @Test
    void liberarTodoElEspacioConfirmadoVuelveAOpen() {
        assertEquals(OPEN, TripStatusPolicy.afterRelease(FILLING, BigDecimal.ZERO));
        assertEquals(FILLING, TripStatusPolicy.afterRelease(FILLING, new BigDecimal("10")));
        assertEquals(CLOSED, TripStatusPolicy.afterRelease(CLOSED, BigDecimal.ZERO));
    }
}
