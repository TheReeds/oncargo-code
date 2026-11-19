package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La PYME invita a sus transportistas preferidos a ofertar por una solicitud recién publicada (roadmap 2.9). */
public record CarriersInvited(UUID requestId, UUID requesterOrgId, String requesterName, String route,
                              LocalDate pickupDate, BigDecimal weightKg, List<UUID> carrierOrgIds)
        implements OutboxEvent {}
