package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/** El transportista respondió al pedido de confirmación de la PYME. */
public record BackupResponded(UUID requestId, UUID bidId, UUID requesterOrgId, UUID carrierOrgId,
                              String carrierName, String route, boolean accepted) implements OutboxEvent {}
