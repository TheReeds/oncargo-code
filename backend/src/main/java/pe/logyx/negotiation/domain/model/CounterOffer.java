package pe.logyx.negotiation.domain.model;

import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contra-oferta sobre una oferta: la propone la PYME (RF27) o el transportista (RF26) y la acepta o rechaza la otra
 * parte (RF28). Una sola vigente por oferta: responder con otra contra-oferta deja la anterior como rechazada.
 */
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
        return propose(bidId, requestId, requesterOrgId, carrierOrgId, requesterOrgId, offeredByProfileId,
                currentAmount, amount, message);
    }

    /** {@code offeredByOrgId}: la PYME o el transportista de la oferta. */
    public static CounterOffer propose(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId,
                                       UUID offeredByOrgId, UUID offeredByProfileId, BigDecimal currentAmount,
                                       BigDecimal amount, String message) {
        if (!offeredByOrgId.equals(requesterOrgId) && !offeredByOrgId.equals(carrierOrgId)) {
            throw BusinessException.forbidden("Solo las partes de la oferta proponen contra-ofertas");
        }
        if (amount.compareTo(currentAmount) == 0) {
            throw BusinessException.badRequest("LOGYX-NEG-003",
                    "La contra-oferta debe proponer un monto distinto al de la oferta", "amount");
        }
        ContactInfoPolicy.ensureNoContactInfo(message, "message");                                          // RN07
        String clean = message == null || message.isBlank() ? null : message.trim();
        return new CounterOffer(UUID.randomUUID(), bidId, requestId, requesterOrgId, carrierOrgId, offeredByOrgId,
                offeredByProfileId, currentAmount, amount, clean, CounterOfferStatus.PENDING, OffsetDateTime.now(), null);
    }

    /** La propuso el transportista (RF26). */
    public boolean byCarrier() { return offeredByOrgId.equals(carrierOrgId); }

    /** Quién debe responderla: la otra parte. */
    public UUID recipientOrgId() { return byCarrier() ? requesterOrgId : carrierOrgId; }

    public boolean isPending() { return status == CounterOfferStatus.PENDING; }

    /** Vence sin respuesta: la oferta se adjudicó a otra, se retiró o se adjudicó por otro monto (F40). */
    public void expire(OffsetDateTime now) {
        if (status != CounterOfferStatus.PENDING) return;
        status = CounterOfferStatus.EXPIRED;
        respondedAt = now;
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
