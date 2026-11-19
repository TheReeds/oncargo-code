package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.domain.BusinessException;

import java.util.UUID;

/**
 * 2.13: asigna conductor y vehículo de un envío en un solo paso (una transacción: si uno falla no queda ninguno).
 * Reutiliza {@link AssignDriverUseCase} y {@link ShipmentLifecycleUseCase#changeVehicle} con todas sus reglas
 * (F11, RN02, motivo si ya se recogió). Un vehículo igual al actual no se toca.
 */
@ApplicationScoped
public class AssignResourcesUseCase {

    @Inject AssignDriverUseCase driver;
    @Inject ShipmentLifecycleUseCase lifecycle;
    @Inject ShipmentQueries queries;

    @Transactional
    public ShipmentSummary assign(UUID shipmentId, UUID driverId, UUID vehicleId, String reason) {
        if (driverId == null && vehicleId == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Elige un conductor, un vehículo o ambos", "driverId");
        }
        ShipmentSummary current = queries.detail(shipmentId);
        if (vehicleId != null && !vehicleId.equals(current.shipment().vehicleId())) {
            lifecycle.changeVehicle(shipmentId, vehicleId, reason);
        }
        if (driverId != null) {
            driver.assign(shipmentId, driverId, reason);
        }
        return queries.detail(shipmentId);
    }
}
