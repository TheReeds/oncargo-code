package pe.logyx.trip.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.trip.domain.model.BookingStatus;
import pe.logyx.trip.domain.model.TripBooking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_bookings")
public class TripBookingEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "trip_id")
    public UUID tripId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "requested_by")
    public UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_city")
    public City originCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_city")
    public City destinationCity;

    @Column(name = "departure_at")
    public OffsetDateTime departureAt;

    @Column(name = "weight_kg")
    public BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_type")
    public CargoType cargoType;

    @Column(name = "cargo_description")
    public String cargoDescription;

    @Column(name = "pickup_address")
    public String pickupAddress;

    @Column(name = "delivery_address")
    public String deliveryAddress;

    public BigDecimal price;

    @Enumerated(EnumType.STRING)
    public BookingStatus status;

    @Column(name = "decision_note")
    public String decisionNote;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "decided_at")
    public OffsetDateTime decidedAt;

    TripBooking toDomain() {
        return new TripBooking(id, tripId, requesterOrgId, carrierOrgId, requestedBy, originCity, destinationCity,
                departureAt, weightKg, cargoType, cargoDescription, pickupAddress, deliveryAddress, price, status,
                decisionNote, requestId, shipmentId, createdAt, decidedAt);
    }

    void apply(TripBooking b) {
        id = b.id();
        tripId = b.tripId();
        requesterOrgId = b.requesterOrgId();
        carrierOrgId = b.carrierOrgId();
        requestedBy = b.requestedBy();
        originCity = b.originCity();
        destinationCity = b.destinationCity();
        departureAt = b.departureAt();
        weightKg = b.weightKg();
        cargoType = b.cargoType();
        cargoDescription = b.cargoDescription();
        pickupAddress = b.pickupAddress();
        deliveryAddress = b.deliveryAddress();
        price = b.price();
        status = b.status();
        decisionNote = b.decisionNote();
        requestId = b.requestId();
        shipmentId = b.shipmentId();
        createdAt = b.createdAt();
        decidedAt = b.decidedAt();
    }
}
