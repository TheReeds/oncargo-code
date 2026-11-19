package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.StopCompletionPolicy;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StopCompletionPolicyTest {

    private static final Shipment.PickupRequirements BOTH = new Shipment.PickupRequirements(true, true);

    private static String code(Runnable r) { return assertThrows(BusinessException.class, r::run).code(); }

    @Test
    void elRecojoExigeLoQuePidioLaPyme() {
        assertEquals("LOGYX-TRK-009", code(() -> StopCompletionPolicy.validate(ShipmentStop.Type.PICKUP, BOTH, false,
                new BigDecimal("100"), null, null)));
        assertEquals("LOGYX-TRK-010", code(() -> StopCompletionPolicy.validate(ShipmentStop.Type.PICKUP, BOTH, true,
                null, null, null)));
        assertEquals("LOGYX-TRK-010", code(() -> StopCompletionPolicy.validate(ShipmentStop.Type.PICKUP,
                Shipment.PickupRequirements.NONE, false, new BigDecimal("60001"), null, null)));
        var d = StopCompletionPolicy.validate(ShipmentStop.Type.PICKUP, BOTH, true, new BigDecimal("2150.5"), "x", "y");
        assertEquals(new BigDecimal("2150.50"), d.loadedWeightKg());
        assertNull(d.receivedByName(), "en el recojo no hay receptor");
        assertEquals(StopCompletionPolicy.Data.NONE, StopCompletionPolicy.validate(ShipmentStop.Type.PICKUP,
                Shipment.PickupRequirements.NONE, false, null, null, null));
    }

    @Test
    void laEntregaNormalizaElReceptor() {
        var d = StopCompletionPolicy.validate(ShipmentStop.Type.DELIVERY, BOTH, false, new BigDecimal("1"),
                "  Ana   Quispe ", "4567 8901");
        assertEquals("Ana Quispe", d.receivedByName());
        assertEquals("45678901", d.receivedByDoc());
        assertNull(d.loadedWeightKg());
        assertEquals("001234567", StopCompletionPolicy.validate(ShipmentStop.Type.DELIVERY, BOTH, false, null, null,
                "001234567").receivedByDoc(), "carné de extranjería");
        assertEquals("LOGYX-TRK-011", code(() -> StopCompletionPolicy.validate(ShipmentStop.Type.DELIVERY, BOTH, false,
                null, "A", null)));
        assertEquals("LOGYX-TRK-011", code(() -> StopCompletionPolicy.validate(ShipmentStop.Type.DELIVERY, BOTH, false,
                null, null, "12-34")));
    }
}
