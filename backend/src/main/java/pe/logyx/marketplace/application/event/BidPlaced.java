package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de dominio: un transportista ofertó en una solicitud (la PYME debe enterarse). {@code bidCount} (total de
 * ofertas tras esta) y {@code auctionClosed} (RN04 por máximo de ofertas) los usa el tiempo real; pueden venir
 * {@code null} en eventos encolados antes de la fase 6.
 */
public record BidPlaced(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, String carrierName,
                        BigDecimal amount, String route, Integer bidCount, Boolean auctionClosed) implements OutboxEvent {}
