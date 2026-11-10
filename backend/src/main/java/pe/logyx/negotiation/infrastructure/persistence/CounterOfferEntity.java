package pe.logyx.negotiation.infrastructure.persistence;

import pe.logyx.negotiation.domain.model.CounterOfferStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.negotiation.domain.model.CounterOffer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "counter_offers")
public class CounterOfferEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "bid_id")
    public UUID bidId;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "offered_by_org_id")
    public UUID offeredByOrgId;

    @Column(name = "offered_by_profile_id")
    public UUID offeredByProfileId;

    @Column(name = "previous_amount")
    public BigDecimal previousAmount;

    public BigDecimal amount;
    public String message;

    @Enumerated(EnumType.STRING)
    public CounterOfferStatus status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "responded_at")
    public OffsetDateTime respondedAt;

    CounterOffer toDomain() {
        return new CounterOffer(id, bidId, requestId, requesterOrgId, carrierOrgId, offeredByOrgId,
                offeredByProfileId, previousAmount, amount, message, status, createdAt, respondedAt);
    }

    void apply(CounterOffer c) {
        id = c.id();
        bidId = c.bidId();
        requestId = c.requestId();
        requesterOrgId = c.requesterOrgId();
        carrierOrgId = c.carrierOrgId();
        offeredByOrgId = c.offeredByOrgId();
        offeredByProfileId = c.offeredByProfileId();
        previousAmount = c.previousAmount();
        amount = c.amount();
        message = c.message();
        status = c.status();
        createdAt = c.createdAt();
        respondedAt = c.respondedAt();
    }
}
