package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/** Evento de dominio: un transportista ofertó en una solicitud (la PYME debe enterarse). */
public record BidPlaced(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, String carrierName,
                        BigDecimal amount, String route) implements OutboxEvent {}
