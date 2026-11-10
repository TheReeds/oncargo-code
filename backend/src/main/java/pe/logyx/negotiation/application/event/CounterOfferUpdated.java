package pe.logyx.negotiation.application.event;

import pe.logyx.negotiation.domain.model.CounterOfferStatus;
import pe.logyx.negotiation.domain.model.CounterOffer;

import java.math.BigDecimal;
import java.util.UUID;

/** Evento de dominio: contra-oferta propuesta (avisar al transportista) o respondida (avisar a la PYME). */
public record CounterOfferUpdated(UUID counterOfferId, UUID bidId, UUID requestId, UUID requesterOrgId,
                                  UUID carrierOrgId, CounterOfferStatus status, BigDecimal amount) {}
