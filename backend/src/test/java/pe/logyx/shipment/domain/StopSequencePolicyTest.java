package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RN08: paradas en orden secuencial. */
class StopSequencePolicyTest {

    private final OffsetDateTime now = OffsetDateTime.now();
    private final Shipment shipment = Shipment.contract(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "ABC-123", new BigDecimal("1500"), null);
    private final ShipmentStop pickup = ShipmentStop.plan(shipment, 1, ShipmentStop.Type.PICKUP, City.LIMA, "Callao");
    private final ShipmentStop delivery = ShipmentStop.plan(shipment, 2, ShipmentStop.Type.DELIVERY, City.PUNO, "Centro");
    // Desordenadas a propósito: la política ordena por secuencia.
    private final List<ShipmentStop> stops = List.of(delivery, pickup);

    @Test
    void noSePuedeSaltarALaEntrega() {
        var e = assertThrows(BusinessException.class, () -> StopSequencePolicy.requireNext(stops, delivery.id()));
        assertEquals("LOGYX-TRK-001", e.code());
    }

    @Test
    void recorreLasParadasEnOrdenHastaEntregar() {
        assertSame(pickup, StopSequencePolicy.requireNext(stops, pickup.id()));
        pickup.arrive(now);
        pickup.complete(now);
        var p1 = shipment.onStopCompleted(pickup.type(), StopSequencePolicy.allCompleted(stops), now);
        assertTrue(p1.tripStarted());
        assertFalse(p1.delivered());
        assertEquals(Shipment.Status.IN_TRANSIT, shipment.status());

        assertSame(delivery, StopSequencePolicy.requireNext(stops, delivery.id()));
        delivery.arrive(now);
        delivery.complete(now);
        var p2 = shipment.onStopCompleted(delivery.type(), StopSequencePolicy.allCompleted(stops), now);
        assertTrue(p2.delivered());
        assertEquals(Shipment.Status.DELIVERED, shipment.status());

        var e = assertThrows(BusinessException.class, () -> StopSequencePolicy.requireNext(stops, delivery.id()));
        assertEquals("LOGYX-TRK-007", e.code(), "una parada ya completada no se vuelve a tocar (F49)");
    }

    @Test
    void completarExigeLlegadaPreviaYLlegarDosVecesFalla() {
        assertEquals("LOGYX-TRK-003", assertThrows(BusinessException.class, () -> pickup.complete(now)).code());
        pickup.arrive(now);
        assertEquals("LOGYX-TRK-002", assertThrows(BusinessException.class, () -> pickup.arrive(now)).code());
    }
}
