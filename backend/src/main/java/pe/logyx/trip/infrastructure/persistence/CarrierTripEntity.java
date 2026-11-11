package pe.logyx.trip.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripPricingMode;
import pe.logyx.trip.domain.model.TripStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "carrier_trips")
public class CarrierTripEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type")
    public Vehicle.Type vehicleType;

    @Column(name = "vehicle_plate")
    public String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_city")
    public City originCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_city")
    public City destinationCity;

    @Column(name = "departure_at")
    public OffsetDateTime departureAt;

    @Column(name = "capacity_kg")
    public BigDecimal capacityKg;

    @Column(name = "confirmed_weight_kg")
    public BigDecimal confirmedWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode")
    public TripPricingMode pricingMode;

    public BigDecimal rate;
    public String notes;

    @Enumerated(EnumType.STRING)
    public TripStatus status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    CarrierTrip toDomain() {
        return new CarrierTrip(id, carrierOrgId, vehicleId, vehicleType, vehiclePlate, originCity, destinationCity,
                departureAt, capacityKg, confirmedWeightKg, pricingMode, rate, notes, status, createdAt);
    }

    void apply(CarrierTrip t) {
        id = t.id();
        carrierOrgId = t.carrierOrgId();
        vehicleId = t.vehicleId();
        vehicleType = t.vehicleType();
        vehiclePlate = t.vehiclePlate();
        originCity = t.originCity();
        destinationCity = t.destinationCity();
        departureAt = t.departureAt();
        capacityKg = t.capacityKg();
        confirmedWeightKg = t.confirmedWeightKg();
        pricingMode = t.pricingMode();
        rate = t.rate();
        notes = t.notes();
        status = t.status();
        createdAt = t.createdAt();
    }
}
