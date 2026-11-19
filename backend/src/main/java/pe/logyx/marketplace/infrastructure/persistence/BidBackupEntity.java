package pe.logyx.marketplace.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.marketplace.domain.model.BackupState;
import pe.logyx.marketplace.domain.model.BidBackup;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bid_backups")
public class BidBackupEntity extends PanacheEntityBase {

    @Id
    @Column(name = "bid_id")
    public UUID bidId;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    public int rank;

    @Enumerated(EnumType.STRING)
    public BackupState state;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "requested_at")
    public OffsetDateTime requestedAt;

    @Column(name = "responded_at")
    public OffsetDateTime respondedAt;

    @Column(name = "used_at")
    public OffsetDateTime usedAt;

    BidBackup toDomain() {
        return new BidBackup(bidId, requestId, requesterOrgId, carrierOrgId, rank, state, createdAt, requestedAt,
                respondedAt, usedAt);
    }

    void apply(BidBackup b) {
        bidId = b.bidId();
        requestId = b.requestId();
        requesterOrgId = b.requesterOrgId();
        carrierOrgId = b.carrierOrgId();
        rank = b.rank();
        state = b.state();
        createdAt = b.createdAt();
        requestedAt = b.requestedAt();
        respondedAt = b.respondedAt();
        usedAt = b.usedAt();
    }
}
