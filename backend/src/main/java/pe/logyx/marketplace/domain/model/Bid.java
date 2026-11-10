package pe.logyx.marketplace.domain.model;

import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Oferta de un transportista en una subasta. */
public final class Bid {

    public enum Status { PENDING, ACCEPTED, REJECTED }

    private final UUID id;
    private final UUID auctionId;
    private final UUID requestId;
    /** Denormalizado para la política RLS bids_requester_* (ADR-0001). */
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final UUID vehicleId;
    /** Copias al ofertar: la flota es privada del transportista (RLS) y la PYME debe ver qué vehículo se ofrece. */
    private final Vehicle.Type vehicleType;
    private final String vehiclePlate;
    private final BigDecimal amount;
    private final String notes;
    private Status status;
    private final OffsetDateTime createdAt;

    public Bid(UUID id, UUID auctionId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, UUID vehicleId,
               Vehicle.Type vehicleType, String vehiclePlate,
               BigDecimal amount, String notes, Status status, OffsetDateTime createdAt) {
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
    }

    public static Bid place(Auction auction, ShipmentRequest request, UUID carrierOrgId, Vehicle vehicle,
                            BigDecimal amount, String notes) {
        return new Bid(UUID.randomUUID(), auction.id(), request.id(), request.requesterOrgId(), carrierOrgId,
                vehicle.id(), vehicle.type(), vehicle.plate(), amount, notes, Status.PENDING, OffsetDateTime.now());
    }

    public boolean isPending() { return status == Status.PENDING; }

    public void accept() { status = Status.ACCEPTED; }

    public void reject() { status = Status.REJECTED; }

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
}
