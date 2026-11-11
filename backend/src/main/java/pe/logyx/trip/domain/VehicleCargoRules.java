package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.domain.BusinessException;

/**
 * Qué carga puede ir en qué vehículo, para viajes publicados, retornos y Smart Load: los refrigerados llevan solo
 * perecederos y los perecederos solo van en refrigerados (coherente con {@code VehicleRequirementPolicy}).
 */
public final class VehicleCargoRules {

    private VehicleCargoRules() {}

    public static boolean canCarry(Vehicle.Type vehicle, CargoType cargo) {
        boolean reefer = vehicle == Vehicle.Type.REFRIGERATED;
        return reefer == (cargo == CargoType.PERISHABLE);
    }

    public static void ensureCanCarry(Vehicle.Type vehicle, CargoType cargo) {
        if (!canCarry(vehicle, cargo)) {
            throw BusinessException.conflict("LOGYX-TRP-010",
                    "Este viaje no admite carga " + cargo.label.toLowerCase() + " por el tipo de vehículo", "cargoType");
        }
    }
}
