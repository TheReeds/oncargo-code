package pe.logyx.marketplace.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Subasta inversa asociada a una solicitud (una por solicitud). */
public final class Auction {

    public enum Status { OPEN, CLOSED, CANCELLED }
    public enum CloseReason { TIMEOUT, MAX_BIDS, ACCEPTED, CANCELLED }

    private final UUID id;
    private final UUID requestId;
    private final OffsetDateTime opensAt;
    private final OffsetDateTime closesAt;
    private final int maxBids;
    private Status status;
    private CloseReason closeReason;
    private OffsetDateTime closedAt;

    public Auction(UUID id, UUID requestId, OffsetDateTime opensAt, OffsetDateTime closesAt, int maxBids,
                   Status status, CloseReason closeReason, OffsetDateTime closedAt) {
        this.id = id;
        this.requestId = requestId;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.maxBids = maxBids;
        this.status = status;
        this.closeReason = closeReason;
        this.closedAt = closedAt;
    }

    public static Auction open(UUID requestId, OffsetDateTime opensAt, OffsetDateTime closesAt, int maxBids) {
        return new Auction(UUID.randomUUID(), requestId, opensAt, closesAt, maxBids, Status.OPEN, null, null);
    }

    public boolean isOpen() { return status == Status.OPEN; }

    public boolean acceptsBids(OffsetDateTime now) {
        return status == Status.OPEN && now.isBefore(closesAt);
    }

    public void close(CloseReason reason, OffsetDateTime now) {
        status = reason == CloseReason.CANCELLED ? Status.CANCELLED : Status.CLOSED;
        closeReason = reason;
        closedAt = now;
    }

    /** Cierra solo si sigue abierta (aceptar/cancelar tras un cierre por tiempo o por ofertas). */
    public void closeIfOpen(CloseReason reason, OffsetDateTime now) {
        if (isOpen()) close(reason, now);
    }

    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public OffsetDateTime opensAt() { return opensAt; }
    public OffsetDateTime closesAt() { return closesAt; }
    public int maxBids() { return maxBids; }
    public Status status() { return status; }
    public CloseReason closeReason() { return closeReason; }
    public OffsetDateTime closedAt() { return closedAt; }
}
