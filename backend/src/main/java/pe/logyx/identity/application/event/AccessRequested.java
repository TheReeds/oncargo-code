package pe.logyx.identity.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/**
 * Una persona pidió a los ADMIN de su organización un permiso que su rol no tiene (roadmap v2, 1.7/1.15). Va por el
 * outbox: {@code notification} avisa a cada ADMIN ({@code adminProfileIds}) para que le cambie el rol. Formato
 * persistido: agrega campos, no los renombres.
 */
public record AccessRequested(UUID organizationId, UUID requesterProfileId, String requesterName, String requesterRole,
                              String permission, String reason, List<UUID> adminProfileIds) implements OutboxEvent {}
