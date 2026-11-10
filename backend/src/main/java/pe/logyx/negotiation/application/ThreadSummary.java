package pe.logyx.negotiation.application;

import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.negotiation.domain.model.Message;

import java.util.UUID;

/** Hilo de una solicitud con un transportista: su oferta (si ofertó) y el último mensaje. */
public record ThreadSummary(UUID requestId, UUID carrierOrgId, String carrierName, Bid bid, long messageCount,
                            Message lastMessage) {}
