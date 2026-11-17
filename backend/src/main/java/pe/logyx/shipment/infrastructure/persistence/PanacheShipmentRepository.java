package pe.logyx.shipment.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shipment.application.OperatorShipmentFilter;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheShipmentRepository implements ShipmentRepository {

    private static final java.time.ZoneId LIMA = java.time.ZoneId.of("America/Lima");

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
    public List<Shipment> listActiveByVehicle(UUID vehicleId) {
        return map(ShipmentEntity.list("vehicleId = ?1 and status in ?2 order by createdAt",
                vehicleId, List.of(Shipment.Status.PENDING, Shipment.Status.IN_TRANSIT)));
    }

    @Override
    public void lockResource(String key) {
        ShipmentEntity.getEntityManager()
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))) l")
                .setParameter(1, key).getSingleResult();
    }

    @Override
    public long countByStatus(UUID orgId, boolean asCarrier, Collection<Shipment.Status> statuses) {
        return ShipmentEntity.count((asCarrier ? "carrierOrgId" : "requesterOrgId") + " = ?1 and status in ?2",
                orgId, statuses);
    }

    @Override
    public long countDeliveredSince(UUID orgId, boolean asCarrier, OffsetDateTime since) {
        return ShipmentEntity.count((asCarrier ? "carrierOrgId" : "requesterOrgId")
                + " = ?1 and status = ?2 and deliveredAt >= ?3", orgId, Shipment.Status.DELIVERED, since);
    }

    @Override
    public long countAllByStatus(Shipment.Status status) {
        return ShipmentEntity.count("status", status);
    }

    @Override
    public Paged<Shipment> searchAll(OperatorShipmentFilter f, PageRequest page) {
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        if (f.status() != null) where.append(" and status = ?").append(add(params, f.status()));
        // Las ciudades viven en la solicitud de origen (JPQL por nombre de entidad: sin dependencia de clases de marketplace).
        if (f.originCity() != null) {
            where.append(" and requestId in (select r.id from ShipmentRequestEntity r where r.originCity = ?")
                    .append(add(params, f.originCity())).append(")");
        }
        if (f.destinationCity() != null) {
            where.append(" and requestId in (select r.id from ShipmentRequestEntity r where r.destinationCity = ?")
                    .append(add(params, f.destinationCity())).append(")");
        }
        if (f.fromDate() != null) where.append(" and createdAt >= ?").append(add(params, f.fromDate().atStartOfDay(LIMA).toOffsetDateTime()));
        if (f.toDate() != null) where.append(" and createdAt < ?").append(add(params, f.toDate().plusDays(1).atStartOfDay(LIMA).toOffsetDateTime()));
        var query = ShipmentEntity.<ShipmentEntity>find(where + " order by createdAt desc, id", params.toArray())
                .page(page.page(), page.size());
        long total = ShipmentEntity.count(where.toString(), params.toArray());
        return new Paged<>(map(query.list()), page, total);
    }

    private static int add(List<Object> params, Object value) {
        params.add(value);
        return params.size();
    }

    @Override
    public List<Shipment> listByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return map(ShipmentEntity.list("id in ?1 order by createdAt desc", ids));
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
