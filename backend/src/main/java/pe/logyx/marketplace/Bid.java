package pe.logyx.marketplace;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bids")
public class Bid extends PanacheEntityBase {

    public enum Status { PENDING, ACCEPTED, REJECTED }

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "auction_id")
    public UUID auctionId;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    public BigDecimal amount;
    public String notes;

    @Enumerated(EnumType.STRING)
    public Status status = Status.PENDING;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
