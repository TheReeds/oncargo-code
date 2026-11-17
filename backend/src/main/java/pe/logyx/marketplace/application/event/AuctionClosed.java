package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/**
 * La subasta cerró por tiempo (RN04, RF18): aviso a la PYME. Con {@code bidCount == 0} también a los operadores:
 * la solicitud queda asignable (RN10) y la PYME puede reabrirla o republicarla (F07).
 */
public record AuctionClosed(UUID requestId, UUID requesterOrgId, String route, long bidCount) implements OutboxEvent {}
