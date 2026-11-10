package pe.logyx.marketplace.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.marketplace.domain.model.Auction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class AuctionEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "opens_at")
    public OffsetDateTime opensAt;

    @Column(name = "closes_at")
    public OffsetDateTime closesAt;

    @Column(name = "max_bids")
    public int maxBids;

    @Enumerated(EnumType.STRING)
    public Auction.Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason")
    public Auction.CloseReason closeReason;

    @Column(name = "closed_at")
    public OffsetDateTime closedAt;

    Auction toDomain() {
        return new Auction(id, requestId, opensAt, closesAt, maxBids, status, closeReason, closedAt);
    }

    void apply(Auction a) {
        id = a.id();
        requestId = a.requestId();
        opensAt = a.opensAt();
        closesAt = a.closesAt();
        maxBids = a.maxBids();
        status = a.status();
        closeReason = a.closeReason();
        closedAt = a.closedAt();
    }
}
