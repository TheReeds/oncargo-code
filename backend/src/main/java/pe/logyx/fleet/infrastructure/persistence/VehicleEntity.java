package pe.logyx.fleet.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.model.Equipment;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Column(name = "archived_at")
    public OffsetDateTime archivedAt;

    /** Listas separadas por coma (V22): pocas, cerradas y sin consultas por elemento. */
    public String equipment = "";

    @Column(name = "accepted_cargo_types")
    public String acceptedCargoTypes = "";

    Vehicle toDomain() {
        return new Vehicle(id, organizationId, plate, type, maxWeightKg, maxVolumeM3, status, createdAt, archivedAt,
                parse(equipment, Equipment.class), parse(acceptedCargoTypes, CargoType.class));
    }

    private static <E extends Enum<E>> Set<E> parse(String csv, Class<E> type) {
        Set<E> out = EnumSet.noneOf(type);
        if (csv == null || csv.isBlank()) return out;
        Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).forEach(x -> {
            try { out.add(Enum.valueOf(type, x)); } catch (IllegalArgumentException ignored) { /* valor retirado */ }
        });
        return out;
    }

    private static String join(Set<? extends Enum<?>> set) {
        return set.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
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
        archivedAt = v.archivedAt();
        equipment = join(v.equipment());
        acceptedCargoTypes = join(v.acceptedCargoTypes());
    }
}
