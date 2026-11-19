package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** ETA aproximada del seguimiento público: ventana redondeada, camión más lento, descansos y sin inventar si ya pasó. */
class EtaPolicyTest {

    private final OffsetDateTime base = OffsetDateTime.parse("2026-11-18T06:10:00-05:00");

    @Test
    void ventanaConFactorDeCamionYDescansos() {
        // 1 000 min de auto → 1 250 de camión → 2 descansos de 3 h → 1 610 min (26 h 50) desde el recojo
        var w = EtaPolicy.estimate(base, 1000, base);
        assertEquals(OffsetDateTime.parse("2026-11-19T09:00:00-05:00"), w.from());
        assertTrue(w.to().isAfter(w.from().plusHours(8)), "ventana amplia: es una estimación");
        assertEquals(0, w.to().getMinute() % 30);
    }

    @Test
    void sinBaseOSinManejoNoHayEta() {
        assertNull(EtaPolicy.estimate(null, 100, base));
        assertNull(EtaPolicy.estimate(base, 0, base));
    }

    @Test
    void siLaVentanaYaPasoNoSeInventaOtraYSiEmpezoSeRecorta() {
        assertNull(EtaPolicy.estimate(base, 60, base.plusDays(1)));
        var w = EtaPolicy.estimate(base, 60, base.plusMinutes(80));
        assertNotNull(w);
        assertFalse(w.from().isBefore(base.plusMinutes(80)));
        assertTrue(w.to().isAfter(w.from()));
    }
}
