package pe.logyx.marketplace;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.City;
import pe.logyx.pricing.CargoType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_requests")
public class ShipmentRequest extends PanacheEntityBase {

    public enum Status { PUBLISHED, MATCHED, CANCELLED }

    @Id
    @GeneratedValue
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
    public Status status = Status.PUBLISHED;

    @Column(name = "assigned_carrier_org_id")
    public UUID assignedCarrierOrgId;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
