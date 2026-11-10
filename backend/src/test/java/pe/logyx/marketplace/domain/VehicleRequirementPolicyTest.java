package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleRequirementPolicyTest {

    private static Vehicle.Type min(String kg, CargoType t) {
        return VehicleRequirementPolicy.minimumFor(new BigDecimal(kg), t);
    }

    @Test
    void escalonesPorPeso() {
        assertEquals(Vehicle.Type.VAN, min("1", CargoType.GENERAL));
        assertEquals(Vehicle.Type.VAN, min("1500", CargoType.GENERAL));
        assertEquals(Vehicle.Type.TRUCK_SMALL, min("1500.01", CargoType.GENERAL));
        assertEquals(Vehicle.Type.TRUCK_SMALL, min("3000", CargoType.FRAGILE));
        assertEquals(Vehicle.Type.TRUCK_MEDIUM, min("3001", CargoType.GENERAL));
        assertEquals(Vehicle.Type.TRUCK_MEDIUM, min("8000", CargoType.BULK));
        assertEquals(Vehicle.Type.TRUCK_LARGE, min("8001", CargoType.GENERAL));
        assertEquals(Vehicle.Type.TRUCK_LARGE, min("20000", CargoType.GENERAL));
        assertEquals(Vehicle.Type.TRUCK_LARGE, min("40000", CargoType.OVERSIZED));
    }

    @Test
    void perecederoRequiereRefrigerado() {
        assertEquals(Vehicle.Type.REFRIGERATED, min("100", CargoType.PERISHABLE));
        assertEquals(Vehicle.Type.REFRIGERATED, min("15000", CargoType.PERISHABLE));
    }
}
