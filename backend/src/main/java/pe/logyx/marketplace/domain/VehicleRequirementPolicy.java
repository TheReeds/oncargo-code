package pe.logyx.marketplace.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;

/**
 * Vehículo mínimo sugerido para una carga (orientativo para el transportista; la regla
 * que bloquea ofertas sigue siendo la capacidad en kg, RN02):
 * <ul>
 *   <li>carga PERISHABLE → {@code REFRIGERATED};</li>
 *   <li>≤ 1 500 kg → {@code VAN}; ≤ 3 000 → {@code TRUCK_SMALL}; ≤ 8 000 → {@code TRUCK_MEDIUM};
 *       cualquier peso mayor → {@code TRUCK_LARGE} (el escalón más alto).</li>
 * </ul>
 */
public final class VehicleRequirementPolicy {

    private static final BigDecimal VAN_MAX = new BigDecimal("1500");
    private static final BigDecimal SMALL_MAX = new BigDecimal("3000");
    private static final BigDecimal MEDIUM_MAX = new BigDecimal("8000");

    private VehicleRequirementPolicy() {}

    public static Vehicle.Type minimumFor(BigDecimal weightKg, CargoType cargoType) {
        if (cargoType == CargoType.PERISHABLE) return Vehicle.Type.REFRIGERATED;
        if (weightKg.compareTo(VAN_MAX) <= 0) return Vehicle.Type.VAN;
        if (weightKg.compareTo(SMALL_MAX) <= 0) return Vehicle.Type.TRUCK_SMALL;
        if (weightKg.compareTo(MEDIUM_MAX) <= 0) return Vehicle.Type.TRUCK_MEDIUM;
        return Vehicle.Type.TRUCK_LARGE;
    }
}
