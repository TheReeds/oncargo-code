package pe.logyx.trip.domain.model;

import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.trip.domain.SmartLoadPlanner;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Combinación de cargas sugerida a un transportista para uno de sus vehículos (OE07). */
public final class SmartLoadSuggestion {

    /** Una solicitud incluida, con su orden de recojo y de entrega dentro del plan. */
    public record Item(UUID requestId, UUID requesterOrgId, City originCity, City destinationCity, BigDecimal weightKg,
                       BigDecimal income, LocalDate pickupDate, int pickupOrder, int deliveryOrder) {}

    private final UUID id;
    private final UUID carrierOrgId;
    private final UUID vehicleId;
    private final Vehicle.Type vehicleType;
    private final String vehiclePlate;
    private final BigDecimal capacityKg;
    private final BigDecimal totalWeightKg;
    private final BigDecimal combinedIncome;
    private final BigDecimal occupancyPct;
    private SmartLoadStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime expiresAt;
    private OffsetDateTime decidedAt;
    private final List<Item> items;

    public SmartLoadSuggestion(UUID id, UUID carrierOrgId, UUID vehicleId, Vehicle.Type vehicleType,
                               String vehiclePlate, BigDecimal capacityKg, BigDecimal totalWeightKg,
                               BigDecimal combinedIncome, BigDecimal occupancyPct, SmartLoadStatus status,
                               OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime decidedAt,
                               List<Item> items) {
        this.id = id;
        this.carrierOrgId = carrierOrgId;
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.capacityKg = capacityKg;
        this.totalWeightKg = totalWeightKg;
        this.combinedIncome = combinedIncome;
        this.occupancyPct = occupancyPct;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.decidedAt = decidedAt;
        this.items = List.copyOf(items);
    }

    public static SmartLoadSuggestion from(UUID carrierOrgId, SmartLoadPlanner.Plan plan, OffsetDateTime now,
                                           Duration ttl) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < plan.loads().size(); i++) {
            var l = plan.loads().get(i);
            items.add(new Item(l.requestId(), l.requesterOrgId(), l.origin(), l.destination(), l.weightKg(),
                    l.income(), l.pickupDate(), i, plan.deliveryOrder().get(i)));
        }
        var v = plan.vehicle();
        return new SmartLoadSuggestion(UUID.randomUUID(), carrierOrgId, v.vehicleId(), v.type(), v.plate(),
                v.capacityKg(), plan.totalWeightKg(), plan.income(), plan.occupancyPct(), SmartLoadStatus.PENDING,
                now, now.plus(ttl), null, items);
    }

    public boolean isPending() { return status == SmartLoadStatus.PENDING; }

    public boolean isExpired(OffsetDateTime now) { return !expiresAt.isAfter(now); }

    /** Aceptar marca la sugerencia; el servicio oferta en lote (F12). No adjudica: cada PYME decide. */
    public void accept(OffsetDateTime now) {
        ensureDecidable(now);
        status = SmartLoadStatus.ACCEPTED;
        decidedAt = now;
    }

    public void reject(OffsetDateTime now) {
        ensureDecidable(now);
        status = SmartLoadStatus.REJECTED;
        decidedAt = now;
    }

    public void expire(OffsetDateTime now) {
        if (isPending()) {
            status = SmartLoadStatus.EXPIRED;
            decidedAt = now;
        }
    }

    private void ensureDecidable(OffsetDateTime now) {
        if (!isPending()) {
            throw BusinessException.conflict("LOGYX-TRP-008", "La sugerencia ya fue resuelta (" + status + ")", "status");
        }
        if (isExpired(now)) {
            throw BusinessException.conflict("LOGYX-TRP-009", "La sugerencia venció");
        }
    }

    /** Ítems en orden de recojo. */
    public List<Item> items() { return items; }

    /** Plan de ruta: recojos por orden, luego entregas por orden. */
    public List<SmartLoadPlanner.Stop> routePlan() {
        List<SmartLoadPlanner.Stop> out = new ArrayList<>();
        items.stream().sorted(Comparator.comparingInt(Item::pickupOrder))
                .forEach(i -> out.add(new SmartLoadPlanner.Stop(SmartLoadPlanner.StopType.PICKUP, i.originCity(), i.requestId())));
        items.stream().sorted(Comparator.comparingInt(Item::deliveryOrder))
                .forEach(i -> out.add(new SmartLoadPlanner.Stop(SmartLoadPlanner.StopType.DELIVERY, i.destinationCity(), i.requestId())));
        return out;
    }

    public UUID id() { return id; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID vehicleId() { return vehicleId; }
    public Vehicle.Type vehicleType() { return vehicleType; }
    public String vehiclePlate() { return vehiclePlate; }
    public BigDecimal capacityKg() { return capacityKg; }
    public BigDecimal totalWeightKg() { return totalWeightKg; }
    public BigDecimal combinedIncome() { return combinedIncome; }
    public BigDecimal occupancyPct() { return occupancyPct; }
    public SmartLoadStatus status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime expiresAt() { return expiresAt; }
    public OffsetDateTime decidedAt() { return decidedAt; }
}
