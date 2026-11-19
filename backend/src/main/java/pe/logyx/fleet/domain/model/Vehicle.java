package pe.logyx.fleet.domain.model;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
}
