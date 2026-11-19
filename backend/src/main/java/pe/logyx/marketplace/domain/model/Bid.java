package pe.logyx.marketplace.domain.model;

import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Oferta de un transportista en una subasta. */
public final class Bid {

    /** WITHDRAWN: el transportista la retiró mientras la subasta estaba abierta (F08); no puede volver a ofertar. */
    public enum Status { PENDING, ACCEPTED, REJECTED, WITHDRAWN }

    private final UUID id;
    private final UUID auctionId;
    private final UUID requestId;
    /** Denormalizado para la política RLS bids_requester_* (ADR-0001). */
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private UUID vehicleId;
    /** Copias al ofertar: la flota es privada del transportista (RLS) y la PYME debe ver qué vehículo se ofrece. */
    private Vehicle.Type vehicleType;
    private String vehiclePlate;
    /** Cambia solo al aceptar el transportista una contra-oferta (negotiation). */
    private BigDecimal amount;
    private final String notes;
    private Status status;
    private final OffsetDateTime createdAt;
    /** RN10: la registró el operador al asignar manualmente la solicitud, no el transportista. */
    private final boolean assignedByOperator;
    /** Plan Smart Load (o viaje publicado) que agrupa esta oferta con otras en el mismo vehículo (F12). */
    private final UUID loadGroupId;
    /** Sellos copiados al ofertar (V22, 2.4): vencimiento del SOAT / la revisión técnica si estaban vigentes; null si no. */
    private LocalDate soatValidUntil;
    private LocalDate inspectionValidUntil;
    /** Veces que el transportista mejoró la oferta (2.8) y cuándo fue la última; la PYME ve «mejorada». */
    private int improvedCount;
    private OffsetDateTime improvedAt;

    public Bid(UUID id, UUID auctionId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
               Vehicle.Type vehicleType, String vehiclePlate,
               BigDecimal amount, String notes, Status status, OffsetDateTime createdAt, boolean assignedByOperator) {
        this(id, auctionId, requestId, requesterOrgId, carrierOrgId, vehicleId, vehicleType, vehiclePlate, amount,
                notes, status, createdAt, assignedByOperator, null);
    }

    public Bid(UUID id, UUID auctionId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
               Vehicle.Type vehicleType, String vehiclePlate,
               BigDecimal amount, String notes, Status status, OffsetDateTime createdAt, boolean assignedByOperator,
               UUID loadGroupId) {
        this.id = id;
        this.auctionId = auctionId;
        this.requestId = requestId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.vehicleId = vehicleId;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.amount = amount;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
        this.assignedByOperator = assignedByOperator;
        this.loadGroupId = loadGroupId;
    }

    /** Constructor completo (persistencia). */
    public Bid(UUID id, UUID auctionId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
               Vehicle.Type vehicleType, String vehiclePlate, BigDecimal amount, String notes, Status status,
               OffsetDateTime createdAt, boolean assignedByOperator, UUID loadGroupId, LocalDate soatValidUntil,
               LocalDate inspectionValidUntil, int improvedCount, OffsetDateTime improvedAt) {
        this(id, auctionId, requestId, requesterOrgId, carrierOrgId, vehicleId, vehicleType, vehiclePlate, amount, notes,
                status, createdAt, assignedByOperator, loadGroupId);
        this.soatValidUntil = soatValidUntil;
        this.inspectionValidUntil = inspectionValidUntil;
        this.improvedCount = improvedCount;
        this.improvedAt = improvedAt;
    }

    public static Bid place(Auction auction, ShipmentRequest request, UUID carrierOrgId, Vehicle vehicle,
                            BigDecimal amount, String notes) {
        return place(auction, request, carrierOrgId, vehicle, amount, notes, null);
    }

    /** RN07: las notas las lee la PYME; no admiten datos de contacto ({@code notes}). */
    public static Bid place(Auction auction, ShipmentRequest request, UUID carrierOrgId, Vehicle vehicle,
                            BigDecimal amount, String notes, UUID loadGroupId) {
        return new Bid(UUID.randomUUID(), auction.id(), request.id(), request.requesterOrgId(), carrierOrgId,
                vehicle.id(), vehicle.type(), vehicle.plate(), amount, ContactInfoPolicy.clean(notes, "notes"),
                Status.PENDING, OffsetDateTime.now(), false, loadGroupId);
    }

    /** RN10: oferta que registra el operador en nombre del transportista al asignar manualmente. */
    public static Bid assignByOperator(Auction auction, ShipmentRequest request, UUID carrierOrgId, Vehicle vehicle,
                                       BigDecimal amount, String notes) {
        return new Bid(UUID.randomUUID(), auction.id(), request.id(), request.requesterOrgId(), carrierOrgId,
                vehicle.id(), vehicle.type(), vehicle.plate(), amount, notes, Status.PENDING, OffsetDateTime.now(),
                true);
    }

    /** El transportista aceptó una contra-oferta: el monto de la oferta pasa a ser el acordado. */
    public void reviseAmount(BigDecimal newAmount) {
        if (!isPending()) {
            throw BusinessException.conflict("LOGYX-NEG-005",
                    "La oferta ya no admite contra-ofertas");
        }
        this.amount = newAmount;
    }

    /** Copia al ofertar los sellos del vehículo (la PYME no lee la flota). */
    public void stampSeals(LocalDate soatUntil, LocalDate inspectionUntil) {
        this.soatValidUntil = soatUntil;
        this.inspectionValidUntil = inspectionUntil;
    }

    /**
     * 2.8: mejora la oferta con la subasta abierta. Solo puede <b>bajar</b> el monto (nunca subirlo; igual está bien si
     * cambia el vehículo) y/o cambiar el vehículo por otro apto. Debe cambiar algo. No cuenta como oferta nueva (RN04)
     * ni reabre la subasta; no cambia la antigüedad de la oferta (desempata igual que antes).
     *
     * @return la revisión (valores anteriores) para el historial
     */
    public Revision improve(Auction auction, BigDecimal newAmount, Vehicle newVehicle, OffsetDateTime now) {
        if (!isPending() || !auction.acceptsBids(now)) {
            throw BusinessException.conflict("LOGYX-BID-005",
                    "Solo puedes mejorar una oferta pendiente mientras la subasta está abierta");
        }
        BigDecimal target = newAmount == null ? amount : newAmount;
        if (target.compareTo(amount) > 0) {
            throw BusinessException.badRequest("LOGYX-BID-006",
                    "Una oferta solo se puede mejorar bajando el monto: no puedes subirlo", "amount");
        }
        boolean sameVehicle = newVehicle == null || newVehicle.id().equals(vehicleId);
        if (target.compareTo(amount) == 0 && sameVehicle) {
            throw BusinessException.badRequest("LOGYX-BID-007",
                    "Baja el monto o cambia el vehículo para mejorar la oferta", "amount");
        }
        Revision rev = new Revision(amount, target, vehicleId, sameVehicle ? vehicleId : newVehicle.id(), vehiclePlate,
                sameVehicle ? vehiclePlate : newVehicle.plate());
        this.amount = target;
        if (!sameVehicle) {
            this.vehicleId = newVehicle.id();
            this.vehicleType = newVehicle.type();
            this.vehiclePlate = newVehicle.plate();
        }
        this.improvedCount++;
        this.improvedAt = now;
        return rev;
    }

    /** Valores antes y después de una mejora (historial del transportista). */
    public record Revision(BigDecimal previousAmount, BigDecimal newAmount, UUID previousVehicleId, UUID newVehicleId,
                           String previousPlate, String newPlate) {}

    public boolean isPending() { return status == Status.PENDING; }

    public void accept() { status = Status.ACCEPTED; }

    public void reject() { status = Status.REJECTED; }

    /** F08: el transportista retira su oferta mientras la subasta acepta ofertas (LOGYX-BID-005 si no). */
    public void withdraw(Auction auction, OffsetDateTime now) {
        if (!isPending() || !auction.acceptsBids(now)) {
            throw BusinessException.conflict("LOGYX-BID-005",
                    "Solo puedes retirar una oferta pendiente mientras la subasta está abierta");
        }
        status = Status.WITHDRAWN;
    }

    public UUID id() { return id; }
    public UUID auctionId() { return auctionId; }
    public UUID requestId() { return requestId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID vehicleId() { return vehicleId; }
    public Vehicle.Type vehicleType() { return vehicleType; }
    public String vehiclePlate() { return vehiclePlate; }
    public BigDecimal amount() { return amount; }
    public String notes() { return notes; }
    public Status status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
    public boolean assignedByOperator() { return assignedByOperator; }
    public UUID loadGroupId() { return loadGroupId; }
    public LocalDate soatValidUntil() { return soatValidUntil; }
    public LocalDate inspectionValidUntil() { return inspectionValidUntil; }
    public int improvedCount() { return improvedCount; }
    public OffsetDateTime improvedAt() { return improvedAt; }
}
