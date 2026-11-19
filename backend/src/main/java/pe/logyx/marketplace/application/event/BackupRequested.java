package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/** La PYME le pide al transportista de una oferta de respaldo que confirme para adjudicarle la carga (roadmap 2.9). */
public record BackupRequested(UUID requestId, UUID bidId, UUID requesterOrgId, String requesterName,
                              UUID carrierOrgId, String route) implements OutboxEvent {}
