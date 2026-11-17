package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/** La PYME canceló una solicitud publicada (F16): aviso a los transportistas cuyas ofertas quedaron rechazadas. */
public record RequestCancelled(UUID requestId, UUID requesterOrgId, String route, String reason,
                               List<UUID> carrierOrgIds) implements OutboxEvent {}
