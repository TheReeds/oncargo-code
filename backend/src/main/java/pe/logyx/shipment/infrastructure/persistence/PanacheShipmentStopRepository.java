package pe.logyx.shipment.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheShipmentStopRepository implements ShipmentStopRepository {

    @Override
    public List<ShipmentStop> listByShipment(UUID shipmentId) {
        return map(ShipmentStopEntity.list("shipmentId = ?1 order by sequence", shipmentId));
    }

    @Override
    public List<ShipmentStop> listByShipments(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return List.of();
        return map(ShipmentStopEntity.list("shipmentId in ?1 order by sequence", shipmentIds));
    }

    @Override
    public void save(ShipmentStop stop) {
        ShipmentStopEntity e = ShipmentStopEntity.findById(stop.id());
        if (e == null) {
            e = new ShipmentStopEntity();
            e.apply(stop);
            e.persist();
        } else {
            e.apply(stop);
        }
    }

    private static List<ShipmentStop> map(List<ShipmentStopEntity> list) {
        return list.stream().map(ShipmentStopEntity::toDomain).toList();
    }
}
