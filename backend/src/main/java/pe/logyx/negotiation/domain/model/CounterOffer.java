package pe.logyx.negotiation.domain.model;

import pe.logyx.negotiation.domain.ContactInfoPolicy;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Contra-oferta de la PYME sobre una oferta (RF27); el transportista la acepta o rechaza (RF28). */
public final class CounterOffer {

    private final UUID id;
    private final UUID bidId;
    private final UUID requestId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final UUID offeredByOrgId;
    private final UUID offeredByProfileId;
    private final BigDecimal previousAmount;
    private final BigDecimal amount;
    private final String message;
    private CounterOfferStatus status;
    private final OffsetDateTime createdAt;
    private OffsetDateTime respondedAt;

    public CounterOffer(UUID id, UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId,
                        UUID offeredByOrgId, UUID offeredByProfileId, BigDecimal previousAmount, BigDecimal amount,
                        String message, CounterOfferStatus status, OffsetDateTime createdAt, OffsetDateTime respondedAt) {
        this.id = id;
        this.bidId = bidId;
        this.requestId = requestId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.offeredByOrgId = offeredByOrgId;
        this.offeredByProfileId = offeredByProfileId;
        this.previousAmount = previousAmount;
        this.amount = amount;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
    }

    public static CounterOffer propose(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId,
                                       UUID offeredByProfileId, BigDecimal currentAmount, BigDecimal amount,
                                       String message) {
        if (amount.compareTo(currentAmount) == 0) {
            throw BusinessException.badRequest("LOGYX-NEG-003",
                    "La contra-oferta debe proponer un monto distinto al de la oferta", "amount");
        }
        ContactInfoPolicy.ensureNoContactInfo(message, "message");                                          // RN07
        String clean = message == null || message.isBlank() ? null : message.trim();
        return new CounterOffer(UUID.randomUUID(), bidId, requestId, requesterOrgId, carrierOrgId, requesterOrgId,
                offeredByProfileId, currentAmount, amount, clean, CounterOfferStatus.PENDING, OffsetDateTime.now(), null);
    }

    public void accept(OffsetDateTime now) { respond(CounterOfferStatus.ACCEPTED, now); }

    public void reject(OffsetDateTime now) { respond(CounterOfferStatus.REJECTED, now); }

    private void respond(CounterOfferStatus target, OffsetDateTime now) {
        if (status != CounterOfferStatus.PENDING) {
            throw BusinessException.conflict("LOGYX-NEG-007", "La contra-oferta ya fue respondida");
        }
        status = target;
        respondedAt = now;
    }

    public UUID id() { return id; }
    public UUID bidId() { return bidId; }
    public UUID requestId() { return requestId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public UUID offeredByOrgId() { return offeredByOrgId; }
    public UUID offeredByProfileId() { return offeredByProfileId; }
    public BigDecimal previousAmount() { return previousAmount; }
    public BigDecimal amount() { return amount; }
    public String message() { return message; }
    public CounterOfferStatus status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime respondedAt() { return respondedAt; }
}
