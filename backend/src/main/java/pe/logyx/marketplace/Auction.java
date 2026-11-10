package pe.logyx.marketplace;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class Auction extends PanacheEntityBase {

    public enum Status { OPEN, CLOSED, CANCELLED }
    public enum CloseReason { TIMEOUT, MAX_BIDS, ACCEPTED, CANCELLED }

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "opens_at")
    public OffsetDateTime opensAt = OffsetDateTime.now();

    @Column(name = "closes_at")
    public OffsetDateTime closesAt;

    @Column(name = "max_bids")
    public int maxBids;

    @Enumerated(EnumType.STRING)
    public Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    public CloseReason closeReason;

    @Column(name = "closed_at")
    public OffsetDateTime closedAt;

    public boolean acceptsBids(OffsetDateTime now) {
        return status == Status.OPEN && now.isBefore(closesAt);
    }

    public void close(CloseReason reason) {
        status = reason == CloseReason.CANCELLED ? Status.CANCELLED : Status.CLOSED;
        closeReason = reason;
        closedAt = OffsetDateTime.now();
    }

    public static Optional<Auction> forRequest(UUID requestId) {
        return find("requestId", requestId).firstResultOptional();
    }
}
