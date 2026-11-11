package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Evento de dominio: se adjudicó una solicitud (la PYME aceptó una oferta o el operador asignó manualmente, RN10).
 * {@code rejected} son las ofertas que quedaron rechazadas por RN05.
 */
public record BidAccepted(UUID bidId, UUID requestId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                          BigDecimal amount, String route, boolean byOperator, List<RejectedBid> rejected) implements OutboxEvent {

    public record RejectedBid(UUID bidId, UUID carrierOrgId) {}
}
