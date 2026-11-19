package pe.logyx.fleet.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Vehículo de la flota de un transportista. */
public final class Vehicle {

    public enum Type { VAN, TRUCK_SMALL, TRUCK_MEDIUM, TRUCK_LARGE, PLATFORM, REFRIGERATED, TANKER }
    public enum Status { AVAILABLE, IN_ROUTE, MAINTENANCE }

    private final UUID id;
    private final UUID organizationId;
    private final String plate;
    private Type type;
    private BigDecimal maxWeightKg;
    private BigDecimal maxVolumeM3;
    private Status status;
    private final OffsetDateTime createdAt;
    /** Archivado (V20): fuera de las listas y sin poder ofertar; los envíos históricos lo siguen referenciando. */
    private OffsetDateTime archivedAt;
    /** Equipamiento declarado (V22): informativo, lo ven los clientes de la flota propia. */
    private Set<Equipment> equipment = EnumSet.noneOf(Equipment.class);
    /**
     * Tipos de carga que el dueño acepta en este vehículo (V22). Vacío = los que permite la matriz RN02. No vacío
     * RESTRINGE la matriz, nunca la amplía (2.4): {@link #configure} lo valida.
     */
    private Set<CargoType> acceptedCargoTypes = EnumSet.noneOf(CargoType.class);

    public Vehicle(UUID id, UUID organizationId, String plate, Type type, BigDecimal maxWeightKg,
                   BigDecimal maxVolumeM3, Status status, OffsetDateTime createdAt) {
        this(id, organizationId, plate, type, maxWeightKg, maxVolumeM3, status, createdAt, null);
    }

    public Vehicle(UUID id, UUID organizationId, String plate, Type type, BigDecimal maxWeightKg,
                   BigDecimal maxVolumeM3, Status status, OffsetDateTime createdAt, OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
        this.id = id;
        this.organizationId = organizationId;
        this.plate = plate;
        this.type = type;
        this.maxWeightKg = maxWeightKg;
        this.maxVolumeM3 = maxVolumeM3;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Vehicle(UUID id, UUID organizationId, String plate, Type type, BigDecimal maxWeightKg,
                   BigDecimal maxVolumeM3, Status status, OffsetDateTime createdAt, OffsetDateTime archivedAt,
                   Set<Equipment> equipment, Set<CargoType> acceptedCargoTypes) {
        this(id, organizationId, plate, type, maxWeightKg, maxVolumeM3, status, createdAt, archivedAt);
        this.equipment = copy(equipment, Equipment.class);
        this.acceptedCargoTypes = copy(acceptedCargoTypes, CargoType.class);
    }

    public static Vehicle register(UUID organizationId, String plate, Type type, BigDecimal maxWeightKg,
                                   BigDecimal maxVolumeM3) {
        return new Vehicle(UUID.randomUUID(), organizationId, normalizePlate(plate), type, maxWeightKg,
                maxVolumeM3, Status.AVAILABLE, OffsetDateTime.now());
    }

    /** "abc123" / "ABC-123" → "ABC-123". */
    public static String normalizePlate(String plate) {
        String p = plate.toUpperCase().replace("-", "");
        return p.substring(0, 3) + "-" + p.substring(3);
    }

    /** Cambio manual de estado. "En ruta" solo lo controla el envío. */
    public void changeStatus(Status newStatus) {
        if (status == Status.IN_ROUTE || newStatus == Status.IN_ROUTE) {
            throw BusinessException.conflict("LOGYX-FLT-002", "El estado 'en ruta' lo controla el envío");
        }
        status = newStatus;
    }

    public boolean isAvailable() { return status == Status.AVAILABLE && archivedAt == null; }

    /** Solo el mantenimiento y el archivo bloquean ofertar (RN02 por fecha): "en ruta" depende de la agenda. */
    public boolean isBlockedForBids() { return status == Status.MAINTENANCE || archivedAt != null; }

    public boolean isArchived() { return archivedAt != null; }

    /**
     * Equipamiento y tipos de carga aceptados (2.4). Los tipos aceptados solo pueden ser un subconjunto de lo que la
     * matriz RN02 permite para este tipo de vehículo (LOGYX-FLT-009); vacío = sin restricción adicional.
     */
    public void configure(Set<Equipment> newEquipment, Set<CargoType> newAccepted) {
        Set<CargoType> accepted = copy(newAccepted, CargoType.class);
        for (CargoType c : accepted) {
            if (!pe.logyx.fleet.domain.VehicleCargoCompatibility.canCarry(type, c)) {
                throw BusinessException.badRequest("LOGYX-FLT-009", "Un vehículo "
                        + pe.logyx.fleet.domain.VehicleCargoCompatibility.label(type) + " no puede llevar carga «"
                        + c.label.toLowerCase() + "» (RN02): la configuración solo restringe, no amplía", "acceptedCargoTypes");
            }
        }
        this.equipment = copy(newEquipment, Equipment.class);
        this.acceptedCargoTypes = accepted;
    }

    /** El dueño aceptó este tipo de carga en el vehículo (sin restricción propia, acepta todo lo que la matriz permite). */
    public boolean acceptsCargoType(CargoType cargo) {
        return cargo == null || acceptedCargoTypes.isEmpty() || acceptedCargoTypes.contains(cargo);
    }

    private static <E extends Enum<E>> Set<E> copy(Set<E> src, Class<E> type) {
        return src == null || src.isEmpty() ? EnumSet.noneOf(type) : EnumSet.copyOf(src);
    }

    /** Corrige tipo, capacidad y volumen (la placa no cambia). */
    public void revise(Type newType, BigDecimal newMaxWeightKg, BigDecimal newMaxVolumeM3) {
        if (newType != null) type = newType;
        if (newMaxWeightKg != null) maxWeightKg = newMaxWeightKg;
        if (newMaxVolumeM3 != null) maxVolumeM3 = newMaxVolumeM3;
    }

    /** Solo cambia el volumen (el vehículo ya tiene compromisos). */
    public void reviseVolume(BigDecimal newMaxVolumeM3) { maxVolumeM3 = newMaxVolumeM3; }

    public void archive(OffsetDateTime now) {
        if (status == Status.IN_ROUTE) {
            throw BusinessException.conflict("LOGYX-FLT-004", "El vehículo está en ruta: no se puede archivar");
        }
        if (archivedAt == null) archivedAt = now;
    }

    /** El envío salió del punto de recojo. */
    public void startRoute() { status = Status.IN_ROUTE; }

    /** El envío terminó: el vehículo vuelve a estar disponible. */
    public void release() { status = Status.AVAILABLE; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public String plate() { return plate; }
    public Type type() { return type; }
    public BigDecimal maxWeightKg() { return maxWeightKg; }
    public BigDecimal maxVolumeM3() { return maxVolumeM3; }
    public Status status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime archivedAt() { return archivedAt; }
    public Set<Equipment> equipment() { return Collections.unmodifiableSet(equipment); }
    public Set<CargoType> acceptedCargoTypes() { return Collections.unmodifiableSet(acceptedCargoTypes); }
}
