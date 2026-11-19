package pe.logyx.shipment.infrastructure.persistence;

import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.shipment.application.port.out.AdjustmentRepository;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@ApplicationScoped
public class PanacheAdjustmentRepository implements AdjustmentRepository {

    @Override
    public Optional<ShipmentAdjustment> findById(UUID id) {
        return ShipmentAdjustmentEntity.<ShipmentAdjustmentEntity>findByIdOptional(id)
                .map(ShipmentAdjustmentEntity::toDomain);
    }

    @Override
    public List<ShipmentAdjustment> listByShipment(UUID shipmentId) {
        return ShipmentAdjustmentEntity.<ShipmentAdjustmentEntity>list("shipmentId = ?1 order by createdAt", shipmentId)
                .stream().map(ShipmentAdjustmentEntity::toDomain).toList();
    }

    @Override
    public int countByShipment(UUID shipmentId) {
        return (int) ShipmentAdjustmentEntity.count("shipmentId", shipmentId);
    }

    @Override
    public Map<UUID, BigDecimal> acceptedTotals(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        List<Object[]> rows = ShipmentAdjustmentEntity.getEntityManager().createQuery(
                "select a.shipmentId, sum(a.amount) from ShipmentAdjustmentEntity a "
                        + "where a.shipmentId in ?1 and a.status = ?2 group by a.shipmentId", Object[].class)
                .setParameter(1, shipmentIds).setParameter(2, AdjustmentStatus.ACCEPTED).getResultList();
        Map<UUID, BigDecimal> out = new HashMap<>();
        rows.forEach(r -> out.put((UUID) r[0], (BigDecimal) r[1]));
        return out;
    }

    @Override
    public Map<UUID, Long> pendingCounts(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        List<Object[]> rows = ShipmentAdjustmentEntity.getEntityManager().createQuery(
                "select a.shipmentId, count(a) from ShipmentAdjustmentEntity a "
                        + "where a.shipmentId in ?1 and a.status = ?2 group by a.shipmentId", Object[].class)
                .setParameter(1, shipmentIds).setParameter(2, AdjustmentStatus.PENDING).getResultList();
        Map<UUID, Long> out = new HashMap<>();
        rows.forEach(r -> out.put((UUID) r[0], (Long) r[1]));
        return out;
    }

    @Override
    public List<ShipmentAdjustment> dueForEscalation(OffsetDateTime now, int limit) {
        return ShipmentAdjustmentEntity.<ShipmentAdjustmentEntity>find(
                        "status = ?1 and escalatedAt is null and respondBy <= ?2 order by respondBy",
                        AdjustmentStatus.PENDING, now)
                .page(Page.ofSize(limit)).list().stream().map(ShipmentAdjustmentEntity::toDomain).toList();
    }

    @Override
    public List<ShipmentAdjustment> escalatedPending(int limit) {
        return ShipmentAdjustmentEntity.<ShipmentAdjustmentEntity>find(
                        "status = ?1 and escalatedAt is not null order by escalatedAt", AdjustmentStatus.PENDING)
                .page(Page.ofSize(limit)).list().stream().map(ShipmentAdjustmentEntity::toDomain).toList();
    }

    @Override
    public void insert(ShipmentAdjustment a) {
        ShipmentAdjustmentEntity.of(a).persist();
    }

    @Override
    public void update(ShipmentAdjustment a) {
        ShipmentAdjustmentEntity e = ShipmentAdjustmentEntity.findById(a.id());
        if (e == null) throw new IllegalStateException("Ajuste inexistente " + a.id());
        e.applyState(a);
    }
}
