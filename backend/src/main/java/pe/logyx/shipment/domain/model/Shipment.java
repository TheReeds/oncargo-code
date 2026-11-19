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
    /** Cambia solo si el transportista cambia de vehículo (F06). */
    private UUID vehicleId;
    private UUID driverId;
    private final BigDecimal agreedPrice;
    /** Copiada al contrato: la flota es privada del transportista. */
    private String vehiclePlate;
    private String driverName;
    private Status status;
    private final OffsetDateTime createdAt;
    private OffsetDateTime deliveredAt;
    /** Viaje publicado o plan Smart Load que consolida varios envíos en el mismo vehículo (null = carga suelta). */
    private final UUID loadGroupId;
    private Cancellation cancellation;
    /**
     * Tasa de comisión de OnCargo vigente al adjudicar (V18, {@code commission_rate}); nula en envíos anteriores.
     * Un cambio posterior del parámetro no la altera.
     */
    private final BigDecimal commissionRate;

    /** Quién canceló, cuándo y por qué (F06). */
    public record Cancellation(UUID byOrgId, String reason, OffsetDateTime at) {}

    public Shipment(UUID id, UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
                    UUID driverId, BigDecimal agreedPrice, String vehiclePlate, String driverName, Status status,
                    OffsetDateTime createdAt, OffsetDateTime deliveredAt, UUID loadGroupId, Cancellation cancellation) {
        this(id, requestId, bidId, requesterOrgId, carrierOrgId, vehicleId, driverId, agreedPrice, vehiclePlate,
                driverName, status, createdAt, deliveredAt, loadGroupId, cancellation, null);
    }

    public Shipment(UUID id, UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
                    UUID driverId, BigDecimal agreedPrice, String vehiclePlate, String driverName, Status status,
                    OffsetDateTime createdAt, OffsetDateTime deliveredAt, UUID loadGroupId, Cancellation cancellation,
                    BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
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
        this.loadGroupId = loadGroupId;
        this.cancellation = cancellation;
    }

    public static Shipment contract(UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId,
                                    UUID vehicleId, String vehiclePlate, BigDecimal agreedPrice, UUID loadGroupId) {
        return contract(requestId, bidId, requesterOrgId, carrierOrgId, vehicleId, vehiclePlate, agreedPrice,
                loadGroupId, null);
    }

    /** {@code commissionRate}: la tasa vigente al adjudicar (se guarda y no cambia después). */
    public static Shipment contract(UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId,
                                    UUID vehicleId, String vehiclePlate, BigDecimal agreedPrice, UUID loadGroupId,
                                    BigDecimal commissionRate) {
        return new Shipment(UUID.randomUUID(), requestId, bidId, requesterOrgId, carrierOrgId, vehicleId, null,
                agreedPrice, vehiclePlate, null, Status.PENDING, OffsetDateTime.now(), null, loadGroupId, null,
                commissionRate);
    }

    public boolean isParty(UUID orgId) {
        return orgId.equals(requesterOrgId) || orgId.equals(carrierOrgId);
    }

    /**
     * El transportista asigna o reasigna el conductor. Antes del recojo, libremente; ya en ruta (relevo, conductor
     * enfermo) solo con motivo (LOGYX-SHP-006). Un envío cerrado no se reasigna (LOGYX-SHP-001).
     */
    public void assignDriver(UUID driverId, String driverName, String reason) {
        ensureReassignable(reason);
        this.driverId = driverId;
        this.driverName = driverName;
    }

    /** Cambio de vehículo (avería, unidad más adecuada): mismas reglas que reasignar el conductor (F06). */
    public void changeVehicle(UUID vehicleId, String vehiclePlate, String reason) {
        ensureReassignable(reason);
        this.vehicleId = vehicleId;
        this.vehiclePlate = vehiclePlate;
    }

    public void ensureReassignable(String reason) {
        if (!isActive()) {
            throw BusinessException.conflict("LOGYX-SHP-001", "El envío ya está cerrado: no se puede reasignar");
        }
        if (status == Status.IN_TRANSIT && (reason == null || reason.isBlank())) {
            throw BusinessException.badRequest("LOGYX-SHP-006",
                    "Indica el motivo del cambio: la carga ya fue recogida", "reason");
        }
    }

    /** Pendiente o en tránsito. */
    public boolean isActive() { return status == Status.PENDING || status == Status.IN_TRANSIT; }

    /**
     * Cancela un envío que aún no sale (PENDING). Lo usan las reservas de viajes publicados al cancelarse.
     * Un envío en tránsito o cerrado no se cancela: LOGYX-SHP-003.
     */
    public void cancel() {
        if (status != Status.PENDING) {
            throw BusinessException.conflict("LOGYX-SHP-003", "Solo se cancela un envío que aún no salió del recojo");
        }
        status = Status.CANCELLED;
    }

    /**
     * Cancelación con motivo (F06). Las partes solo cancelan antes del recojo; el operador también en tránsito
     * ({@code allowInTransit}), p. ej. carga extraviada (RF55). Motivo obligatorio (LOGYX-SHP-006).
     */
    public void cancel(UUID byOrgId, String reason, boolean allowInTransit, OffsetDateTime now) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("LOGYX-SHP-006", "Indica el motivo de la cancelación", "reason");
        }
        if (!(status == Status.PENDING || (allowInTransit && status == Status.IN_TRANSIT))) {
            throw BusinessException.conflict("LOGYX-SHP-003", allowInTransit
                    ? "El envío ya está cerrado" : "Solo se cancela un envío que aún no salió del recojo");
        }
        status = Status.CANCELLED;
        cancellation = new Cancellation(byOrgId, reason.trim(), now);
    }

    /** El operador da por entregado un envío en tránsito al resolver una incidencia (RF55). */
    public void markDeliveredByOperator(OffsetDateTime now) {
        if (status != Status.IN_TRANSIT) {
            throw BusinessException.conflict("LOGYX-INC-006",
                    "Solo se marca como entregado un envío en tránsito", "shipmentOutcome");
        }
        status = Status.DELIVERED;
        deliveredAt = now;
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
    public UUID loadGroupId() { return loadGroupId; }
    public Cancellation cancellation() { return cancellation; }
    public BigDecimal commissionRate() { return commissionRate; }
}
