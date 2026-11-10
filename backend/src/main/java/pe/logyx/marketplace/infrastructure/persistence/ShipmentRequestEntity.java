package pe.logyx.marketplace.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_requests")
public class ShipmentRequestEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "created_by")
    public UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_city")
    public City originCity;

    @Column(name = "origin_address")
    public String originAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_city")
    public City destinationCity;

    @Column(name = "destination_address")
    public String destinationAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_type")
    public CargoType cargoType;

    @Column(name = "cargo_description")
    public String cargoDescription;

    @Column(name = "weight_kg")
    public BigDecimal weightKg;

    @Column(name = "volume_m3")
    public BigDecimal volumeM3;

    @Column(name = "pickup_date")
    public LocalDate pickupDate;

    @Column(name = "distance_km")
    public BigDecimal distanceKm;

    @Column(name = "suggested_price")
    public BigDecimal suggestedPrice;

    @Column(name = "price_floor")
    public BigDecimal priceFloor;

    @Enumerated(EnumType.STRING)
    public ShipmentRequest.Status status;

    @Column(name = "assigned_carrier_org_id")
    public UUID assignedCarrierOrgId;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    ShipmentRequest toDomain() {
        return new ShipmentRequest(id, requesterOrgId, createdBy,
                new ShipmentRequest.Cargo(originCity, originAddress, destinationCity, destinationAddress,
                        cargoType, cargoDescription, weightKg, volumeM3, pickupDate),
                new ShipmentRequest.Pricing(distanceKm, suggestedPrice, priceFloor),
                status, assignedCarrierOrgId, createdAt);
    }

    void apply(ShipmentRequest r) {
        var c = r.cargo();
        var p = r.pricing();
        id = r.id();
        requesterOrgId = r.requesterOrgId();
        createdBy = r.createdBy();
        originCity = c.originCity();
        originAddress = c.originAddress();
        destinationCity = c.destinationCity();
        destinationAddress = c.destinationAddress();
        cargoType = c.cargoType();
        cargoDescription = c.cargoDescription();
        weightKg = c.weightKg();
        volumeM3 = c.volumeM3();
        pickupDate = c.pickupDate();
        distanceKm = p.distanceKm();
        suggestedPrice = p.suggestedPrice();
        priceFloor = p.priceFloor();
        status = r.status();
        assignedCarrierOrgId = r.assignedCarrierOrgId();
        createdAt = r.createdAt();
    }
}
