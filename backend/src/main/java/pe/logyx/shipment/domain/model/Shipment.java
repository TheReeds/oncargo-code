package pe.logyx.shipment.domain.model;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Envío (contrato) generado al aceptar una oferta. */
public final class Shipment {

    public enum Status { PENDING, IN_TRANSIT, DELIVERED, CANCELLED }

    /** Qué cambió en el viaje al completar una parada. */
    public record Progress(boolean tripStarted, boolean delivered) {}

    private final UUID id;
    private final UUID requestId;
    private final UUID bidId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final UUID vehicleId;
    private UUID driverId;
    private final BigDecimal agreedPrice;
    /** Copiada al contrato: la flota es privada del transportista. */
    private final String vehiclePlate;
    private String driverName;
    private Status status;
    private final OffsetDateTime createdAt;
    private OffsetDateTime deliveredAt;

    public Shipment(UUID id, UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
                    UUID driverId, BigDecimal agreedPrice, String vehiclePlate, String driverName, Status status,
                    OffsetDateTime createdAt, OffsetDateTime deliveredAt) {
        this.id = id;
        this.requestId = requestId;
        this.bidId = bidId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.agreedPrice = agreedPrice;
        this.vehiclePlate = vehiclePlate;
        this.driverName = driverName;
        this.status = status;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
    }

    public static Shipment contract(UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId,
                                    UUID vehicleId, String vehiclePlate, BigDecimal agreedPrice) {
        return new Shipment(UUID.randomUUID(), requestId, bidId, requesterOrgId, carrierOrgId, vehicleId, null,
                agreedPrice, vehiclePlate, null, Status.PENDING, OffsetDateTime.now(), null);
    }

    public boolean isParty(UUID orgId) {
        return orgId.equals(requesterOrgId) || orgId.equals(carrierOrgId);
    }

    /** El transportista asigna (o reasigna) conductor antes del recojo. */
    public void assignDriver(UUID driverId, String driverName) {
        ensureDriverAssignable();
        this.driverId = driverId;
        this.driverName = driverName;
    }

    public void ensureDriverAssignable() {
        if (status != Status.PENDING) {
            throw BusinessException.conflict("LOGYX-SHP-001", "Solo se reasigna conductor antes del recojo");
        }
    }

    public void ensureOpen() {
        if (status == Status.DELIVERED || status == Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-TRK-004", "El envío ya está cerrado");
        }
    }

    /**
     * Completar el recojo inicia el viaje; completar todas las paradas lo
     * entrega.
     */
    public Progress onStopCompleted(ShipmentStop.Type type, boolean allStopsCompleted, OffsetDateTime now) {
        boolean started = false;
        if (type == ShipmentStop.Type.PICKUP && status == Status.PENDING) {
            status = Status.IN_TRANSIT;
            started = true;
        }
        boolean delivered = false;
        if (allStopsCompleted) {
            status = Status.DELIVERED;
            deliveredAt = now;
            delivered = true;
        }
        return new Progress(started, delivered);
    }

    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public UUID bidId() { return bidId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID vehicleId() { return vehicleId; }
    public UUID driverId() { return driverId; }
    public BigDecimal agreedPrice() { return agreedPrice; }
    public String vehiclePlate() { return vehiclePlate; }
    public String driverName() { return driverName; }
    public Status status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime deliveredAt() { return deliveredAt; }
}
