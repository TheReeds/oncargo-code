package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/** El transportista retiró su oferta con la subasta abierta (F08): aviso a la PYME y se vencen sus contra-ofertas. */
public record BidWithdrawn(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, String carrierName,
                           String route, int bidCount) implements OutboxEvent {}
