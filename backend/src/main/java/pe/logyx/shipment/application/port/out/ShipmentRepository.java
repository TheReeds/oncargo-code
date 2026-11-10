package pe.logyx.shipment.application.port.out;

import pe.logyx.shipment.domain.model.Shipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {
    Optional<Shipment> findById(UUID id);
    Optional<Shipment> findForCarrier(UUID id, UUID carrierOrgId);
    Optional<Shipment> findForDriver(UUID id, UUID driverId);
    /** Más recientes primero. */
    List<Shipment> listByCarrier(UUID carrierOrgId);
    /** Más recientes primero. */
    List<Shipment> listByRequester(UUID requesterOrgId);
    /** Pendientes o en tránsito, más antiguos primero. */
    List<Shipment> listActiveByDriver(UUID driverId);
    /** Inserta o actualiza. */
    void save(Shipment shipment);
}
