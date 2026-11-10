package pe.logyx.fleet;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle extends PanacheEntityBase {

    public enum Type { VAN, TRUCK_SMALL, TRUCK_MEDIUM, TRUCK_LARGE, PLATFORM, REFRIGERATED, TANKER }
    public enum Status { AVAILABLE, IN_ROUTE, MAINTENANCE }

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    public String plate;

    @Enumerated(EnumType.STRING)
    public Type type;

    @Column(name = "max_weight_kg")
    public BigDecimal maxWeightKg;

    @Column(name = "max_volume_m3")
    public BigDecimal maxVolumeM3;

    @Enumerated(EnumType.STRING)
    public Status status = Status.AVAILABLE;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
