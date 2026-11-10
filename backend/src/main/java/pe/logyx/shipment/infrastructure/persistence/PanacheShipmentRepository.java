package pe.logyx.shipment.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheShipmentRepository implements ShipmentRepository {

    @Override
    public Optional<Shipment> findById(UUID id) {
        return ShipmentEntity.<ShipmentEntity>findByIdOptional(id).map(ShipmentEntity::toDomain);
    }

    @Override
    public Optional<Shipment> findForCarrier(UUID id, UUID carrierOrgId) {
        return ShipmentEntity.<ShipmentEntity>find("id = ?1 and carrierOrgId = ?2", id, carrierOrgId)
                .firstResultOptional().map(ShipmentEntity::toDomain);
    }

    @Override
    public Optional<Shipment> findForDriver(UUID id, UUID driverId) {
        return ShipmentEntity.<ShipmentEntity>find("id = ?1 and driverId = ?2", id, driverId)
                .firstResultOptional().map(ShipmentEntity::toDomain);
    }

    @Override
    public List<Shipment> listByCarrier(UUID carrierOrgId) {
        return map(ShipmentEntity.list("carrierOrgId = ?1 order by createdAt desc", carrierOrgId));
    }

    @Override
    public List<Shipment> listByRequester(UUID requesterOrgId) {
        return map(ShipmentEntity.list("requesterOrgId = ?1 order by createdAt desc", requesterOrgId));
    }

    @Override
    public List<Shipment> listActiveByDriver(UUID driverId) {
        return map(ShipmentEntity.list("driverId = ?1 and status in ?2 order by createdAt",
                driverId, List.of(Shipment.Status.PENDING, Shipment.Status.IN_TRANSIT)));
    }

    @Override
    public void save(Shipment shipment) {
        ShipmentEntity e = ShipmentEntity.findById(shipment.id());
        if (e == null) {
            e = new ShipmentEntity();
            e.apply(shipment);
            e.persist();
        } else {
            e.apply(shipment);
        }
    }

    private static List<Shipment> map(List<ShipmentEntity> list) {
        return list.stream().map(ShipmentEntity::toDomain).toList();
    }
}
