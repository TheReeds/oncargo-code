package pe.logyx.fleet.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class VehicleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    public String plate;

    @Enumerated(EnumType.STRING)
    public Vehicle.Type type;

    @Column(name = "max_weight_kg")
    public BigDecimal maxWeightKg;

    @Column(name = "max_volume_m3")
    public BigDecimal maxVolumeM3;

    @Enumerated(EnumType.STRING)
    public Vehicle.Status status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Vehicle toDomain() {
        return new Vehicle(id, organizationId, plate, type, maxWeightKg, maxVolumeM3, status, createdAt);
    }

    void apply(Vehicle v) {
        id = v.id();
        organizationId = v.organizationId();
        plate = v.plate();
        type = v.type();
        maxWeightKg = v.maxWeightKg();
        maxVolumeM3 = v.maxVolumeM3();
        status = v.status();
        createdAt = v.createdAt();
    }
}
