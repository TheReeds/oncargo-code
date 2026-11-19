package pe.logyx.alerts.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "saved_searches")
public class SavedSearchEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    @Column(name = "profile_id")
    public UUID profileId;

    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_city")
    public City originCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_city")
    public City destinationCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_type")
    public CargoType cargoType;

    @Column(name = "pickup_from")
    public LocalDate pickupFrom;

    @Column(name = "pickup_to")
    public LocalDate pickupTo;

    @Column(name = "max_weight_kg")
    public BigDecimal maxWeightKg;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    public boolean active;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    SavedSearch toDomain() {
        return new SavedSearch(id, organizationId, profileId, name, originCity, destinationCity, cargoType, pickupFrom,
                pickupTo, maxWeightKg, vehicleId, active, createdAt, updatedAt);
    }

    void apply(SavedSearch s) {
        id = s.id();
        organizationId = s.organizationId();
        profileId = s.profileId();
        name = s.name();
        originCity = s.originCity();
        destinationCity = s.destinationCity();
        cargoType = s.cargoType();
        pickupFrom = s.pickupFrom();
        pickupTo = s.pickupTo();
        maxWeightKg = s.maxWeightKg();
        vehicleId = s.vehicleId();
        active = s.active();
        createdAt = s.createdAt();
        updatedAt = s.updatedAt();
    }
}
