package pe.logyx.trip.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.City;
import pe.logyx.trip.domain.model.SmartLoadSuggestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "smart_load_items")
public class SmartLoadItemEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "suggestion_id")
    public UUID suggestionId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_city")
    public City originCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_city")
    public City destinationCity;

    @Column(name = "weight_kg")
    public BigDecimal weightKg;

    public BigDecimal income;

    @Column(name = "pickup_date")
    public LocalDate pickupDate;

    @Column(name = "pickup_order")
    public int pickupOrder;

    @Column(name = "delivery_order")
    public int deliveryOrder;

    SmartLoadSuggestion.Item toDomain() {
        return new SmartLoadSuggestion.Item(requestId, requesterOrgId, originCity, destinationCity, weightKg, income,
                pickupDate, pickupOrder, deliveryOrder);
    }

    static SmartLoadItemEntity of(SmartLoadSuggestion s, SmartLoadSuggestion.Item i) {
        SmartLoadItemEntity e = new SmartLoadItemEntity();
        e.id = UUID.randomUUID();
        e.suggestionId = s.id();
        e.carrierOrgId = s.carrierOrgId();
        e.requestId = i.requestId();
        e.requesterOrgId = i.requesterOrgId();
        e.originCity = i.originCity();
        e.destinationCity = i.destinationCity();
        e.weightKg = i.weightKg();
        e.income = i.income();
        e.pickupDate = i.pickupDate();
        e.pickupOrder = i.pickupOrder();
        e.deliveryOrder = i.deliveryOrder();
        return e;
    }
}
