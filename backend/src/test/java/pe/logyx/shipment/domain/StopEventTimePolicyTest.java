package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Roadmap 1.11: la hora del dispositivo vale dentro de límites (futuro 2 min, 48 h, monótona). */
class StopEventTimePolicyTest {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-18T10:00:00-05:00");

    private static String code(Runnable r) {
        BusinessException e = assertThrows(BusinessException.class, r::run);
        assertEquals("occurredAt", e.field());
        return e.code();
    }

    @Test
    void sinHoraDelDispositivoValeLaDelServidor() {
        assertEquals(now, StopEventTimePolicy.resolve(null, now, now.minusHours(1)));
        // ... nunca antes del paso previo (reloj del paso anterior un poco adelantado)
        assertEquals(now.plusMinutes(1), StopEventTimePolicy.resolve(null, now, now.plusMinutes(1)));
    }

    @Test
    void aceptaUnaHoraPasadaDentroDeLosLimites() {
        OffsetDateTime t = now.minusHours(47);
        assertEquals(t, StopEventTimePolicy.resolve(t, now, now.minusHours(48)));
        assertEquals(now.plusMinutes(2), StopEventTimePolicy.resolve(now.plusMinutes(2), now, null));
    }

    @Test
    void rechazaFuturaViejaONoMonotona() {
        assertEquals("LOGYX-TRK-008", code(() -> StopEventTimePolicy.resolve(now.plusMinutes(3), now, null)));
        assertEquals("LOGYX-TRK-008", code(() -> StopEventTimePolicy.resolve(now.minusHours(49), now, null)));
        assertEquals("LOGYX-TRK-008", code(() -> StopEventTimePolicy.resolve(now.minusHours(2), now, now.minusHours(1))));
    }
}
