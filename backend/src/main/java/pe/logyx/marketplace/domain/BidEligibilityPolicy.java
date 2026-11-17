package pe.logyx.marketplace.domain;

import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.Money;

import java.math.BigDecimal;

/**
 * Condiciones para que una oferta sea válida:
 * <ul>
 *   <li>RN03: monto ≥ piso de precio de la solicitud (calculado por
 *       {@code pricing.domain.PriceFloorPolicy} al publicar). Error en {@code amount}.</li>
 *   <li>RN02: el vehículo es propio (lo resuelve fleet), está disponible, tiene capacidad para el peso, su tipo
 *       es apto para el tipo de carga y le cabe el volumen declarado (matriz
 *       {@code fleet.domain.VehicleCargoCompatibility}). Errores en {@code vehicleId}.</li>
 * </ul>
 */
public final class BidEligibilityPolicy {

    private BidEligibilityPolicy() {}

    public static void ensureAboveFloor(BigDecimal amount, BigDecimal priceFloor) {
        if (amount.compareTo(priceFloor) < 0) {
            throw BusinessException.badRequest("LOGYX-BID-001",
                    "La oferta no puede ser menor al precio mínimo de " + Money.soles(priceFloor) + " (RN03)",
                    "amount");
        }
    }

    /** RN02 completa: disponibilidad, kg, tipo de carga (LOGYX-CAP-003) y volumen (LOGYX-CAP-004). */
    public static void ensureVehicleEligible(Vehicle v, ShipmentRequest.Cargo cargo) {
        VehicleCargoCompatibility.ensureSuitable(v, cargo.cargoType(), cargo.weightKg(), cargo.volumeM3());
    }

    public static void ensureVehicleEligible(boolean vehicleAvailable, BigDecimal vehicleMaxWeightKg,
                                             BigDecimal cargoWeightKg) {
        if (!vehicleAvailable) {
            throw BusinessException.conflict("LOGYX-CAP-002", "El vehículo no está disponible", "vehicleId");
        }
        if (vehicleMaxWeightKg.compareTo(cargoWeightKg) < 0) {
            throw BusinessException.badRequest("LOGYX-CAP-001", "El vehículo no tiene capacidad suficiente (RN02)",
                    "vehicleId");
        }
    }
}
