package pe.logyx.marketplace.domain;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

/**
 * Condiciones para que una oferta sea válida:
 * <ul>
 *   <li>RN03: monto ≥ piso de precio de la solicitud (calculado por
 *       {@code pricing.domain.PriceFloorPolicy} al publicar).</li>
 *   <li>RN02: el vehículo es propio (lo resuelve fleet), está disponible y
 *       tiene capacidad para el peso.</li>
 * </ul>
 */
public final class BidEligibilityPolicy {

    private BidEligibilityPolicy() {}

    public static void ensureAboveFloor(BigDecimal amount, BigDecimal priceFloor) {
        if (amount.compareTo(priceFloor) < 0) {
            throw BusinessException.badRequest("LOGYX-BID-001",
                    "La oferta no puede ser menor al precio mínimo de S/ " + priceFloor + " (RN03)");
        }
    }

    public static void ensureVehicleEligible(boolean vehicleAvailable, BigDecimal vehicleMaxWeightKg,
                                             BigDecimal cargoWeightKg) {
        if (!vehicleAvailable) {
            throw BusinessException.conflict("LOGYX-CAP-002", "El vehículo no está disponible");
        }
        if (vehicleMaxWeightKg.compareTo(cargoWeightKg) < 0) {
            throw BusinessException.badRequest("LOGYX-CAP-001", "El vehículo no tiene capacidad suficiente (RN02)");
        }
    }
}
