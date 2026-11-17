package pe.logyx.negotiation.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import pe.logyx.negotiation.domain.model.CounterOfferStatus;
import pe.logyx.negotiation.domain.model.CounterOffer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de dominio: contra-oferta propuesta (avisar a la otra parte) o respondida (avisar a quien la propuso).
 * {@code offeredByOrgId} (V13) puede venir null en eventos anteriores: entonces la propuso la PYME.
 */
public record CounterOfferUpdated(UUID counterOfferId, UUID bidId, UUID requestId, UUID requesterOrgId,
                                  UUID carrierOrgId, CounterOfferStatus status, BigDecimal amount,
                                  UUID offeredByOrgId) implements OutboxEvent {

    public CounterOfferUpdated(UUID counterOfferId, UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId,
                               CounterOfferStatus status, BigDecimal amount) {
        this(counterOfferId, bidId, requestId, requesterOrgId, carrierOrgId, status, amount, null);
    }

    /** La propuso el transportista (RF26). */
    public boolean byCarrier() { return offeredByOrgId != null && offeredByOrgId.equals(carrierOrgId); }
}
