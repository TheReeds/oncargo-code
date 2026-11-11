package pe.logyx.trip.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.trip.domain.model.SmartLoadStatus;
import pe.logyx.trip.domain.model.SmartLoadSuggestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "smart_load_suggestions")
public class SmartLoadSuggestionEntity extends PanacheEntityBase {

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

    @Column(name = "capacity_kg")
    public BigDecimal capacityKg;

    @Column(name = "total_weight_kg")
    public BigDecimal totalWeightKg;

    @Column(name = "combined_income")
    public BigDecimal combinedIncome;

    @Column(name = "occupancy_pct")
    public BigDecimal occupancyPct;

    @Enumerated(EnumType.STRING)
    public SmartLoadStatus status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "expires_at")
    public OffsetDateTime expiresAt;

    @Column(name = "decided_at")
    public OffsetDateTime decidedAt;

    SmartLoadSuggestion toDomain(List<SmartLoadSuggestion.Item> items) {
        return new SmartLoadSuggestion(id, carrierOrgId, vehicleId, vehicleType, vehiclePlate, capacityKg,
                totalWeightKg, combinedIncome, occupancyPct, status, createdAt, expiresAt, decidedAt, items);
    }

    void apply(SmartLoadSuggestion s) {
        id = s.id();
        carrierOrgId = s.carrierOrgId();
        vehicleId = s.vehicleId();
        vehicleType = s.vehicleType();
        vehiclePlate = s.vehiclePlate();
        capacityKg = s.capacityKg();
        totalWeightKg = s.totalWeightKg();
        combinedIncome = s.combinedIncome();
        occupancyPct = s.occupancyPct();
        status = s.status();
        createdAt = s.createdAt();
        expiresAt = s.expiresAt();
        decidedAt = s.decidedAt();
    }
}
