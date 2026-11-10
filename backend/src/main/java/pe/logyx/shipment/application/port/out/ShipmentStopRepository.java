package pe.logyx.shipment.application.port.out;

import pe.logyx.shipment.domain.model.ShipmentStop;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShipmentStopRepository {
    /** Por secuencia ascendente. */
    List<ShipmentStop> listByShipment(UUID shipmentId);
    /** Por secuencia ascendente. */
    List<ShipmentStop> listByShipments(Collection<UUID> shipmentIds);
    /** Inserta o actualiza. */
    void save(ShipmentStop stop);
}
