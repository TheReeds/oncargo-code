package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/** El transportista mejoró su oferta (bajó el monto y/o cambió el vehículo): la PYME debe enterarse (2.8). */
public record BidImproved(UUID bidId, UUID requestId, UUID requesterOrgId, UUID carrierOrgId, String carrierName,
                          BigDecimal amount, String route, boolean vehicleChanged, int improvedCount)
        implements OutboxEvent {}
