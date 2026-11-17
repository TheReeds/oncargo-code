package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.domain.BusinessException;

/**
 * Qué carga puede ir en qué vehículo, para viajes publicados, retornos y Smart Load. Delega en la matriz única de
 * RN02 ({@link VehicleCargoCompatibility}); conserva su código de error propio de viajes (LOGYX-TRP-010).
 */
public final class VehicleCargoRules {

    private VehicleCargoRules() {}

    public static boolean canCarry(Vehicle.Type vehicle, CargoType cargo) {
        return VehicleCargoCompatibility.canCarry(vehicle, cargo);
    }

    public static void ensureCanCarry(Vehicle.Type vehicle, CargoType cargo) {
        if (!canCarry(vehicle, cargo)) {
            throw BusinessException.conflict("LOGYX-TRP-010",
                    "Este viaje (" + VehicleCargoCompatibility.label(vehicle) + ") no admite carga de tipo «"
                            + cargo.label.toLowerCase() + "»", "cargoType");
        }
    }
}
