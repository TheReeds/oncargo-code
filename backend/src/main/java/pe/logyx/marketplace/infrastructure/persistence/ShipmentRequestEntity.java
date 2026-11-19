package pe.logyx.marketplace.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

    /** V24 (2.14): versión del tarifario de la cotización; nula = anterior a V24. */
    @Column(name = "tariff_version")
    public Integer tariffVersion;

    @Enumerated(EnumType.STRING)
    public ShipmentRequest.Status status;

    @Column(name = "assigned_carrier_org_id")
    public UUID assignedCarrierOrgId;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "cancel_reason")
    public String cancelReason;

    @Column(name = "republished_from")
    public UUID republishedFrom;

    public String code;

    @Column(name = "customer_reference")
    public String customerReference;

    @Column(name = "declared_value")
    public BigDecimal declaredValue;

    @Column(name = "pickup_contact_name")
    public String pickupContactName;
    @Column(name = "pickup_contact_phone")
    public String pickupContactPhone;
    @Column(name = "pickup_window_from")
    public LocalTime pickupWindowFrom;
    @Column(name = "pickup_window_to")
    public LocalTime pickupWindowTo;
    @Column(name = "pickup_instructions")
    public String pickupInstructions;

    @Column(name = "delivery_contact_name")
    public String deliveryContactName;
    @Column(name = "delivery_contact_phone")
    public String deliveryContactPhone;
    @Column(name = "delivery_window_from")
    public LocalTime deliveryWindowFrom;
    @Column(name = "delivery_window_to")
    public LocalTime deliveryWindowTo;
    @Column(name = "delivery_instructions")
    public String deliveryInstructions;

    ShipmentRequest toDomain() {
        return new ShipmentRequest(id, requesterOrgId, createdBy,
                new ShipmentRequest.Cargo(originCity, originAddress, destinationCity, destinationAddress,
                        cargoType, cargoDescription, weightKg, volumeM3, pickupDate),
                new ShipmentRequest.Pricing(distanceKm, suggestedPrice, priceFloor, tariffVersion),
                status, assignedCarrierOrgId, createdAt, cancelReason, republishedFrom, code,
                new ShipmentRequest.Details(customerReference, declaredValue,
                        new ShipmentRequest.StopDetails(pickupContactName, pickupContactPhone, pickupWindowFrom,
                                pickupWindowTo, pickupInstructions),
                        new ShipmentRequest.StopDetails(deliveryContactName, deliveryContactPhone, deliveryWindowFrom,
                                deliveryWindowTo, deliveryInstructions)));
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
        tariffVersion = p.tariffVersion();
        status = r.status();
        assignedCarrierOrgId = r.assignedCarrierOrgId();
        createdAt = r.createdAt();
        cancelReason = r.cancelReason();
        republishedFrom = r.republishedFrom();
        code = r.code();
        var d = r.details();
        customerReference = d.customerReference();
        declaredValue = d.declaredValue();
        pickupContactName = d.pickup().contactName();
        pickupContactPhone = d.pickup().contactPhone();
        pickupWindowFrom = d.pickup().windowFrom();
        pickupWindowTo = d.pickup().windowTo();
        pickupInstructions = d.pickup().instructions();
        deliveryContactName = d.delivery().contactName();
        deliveryContactPhone = d.delivery().contactPhone();
        deliveryWindowFrom = d.delivery().windowFrom();
        deliveryWindowTo = d.delivery().windowTo();
        deliveryInstructions = d.delivery().instructions();
    }
}
