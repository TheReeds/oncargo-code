package pe.logyx.incident.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/** El operador asignó una incidencia a otra persona del operador (2.7): aviso personal al responsable. */
public record IncidentAssigned(UUID incidentId, UUID shipmentId, UUID operatorOrgId, UUID assigneeProfileId,
                               UUID assignedByProfileId, String category) implements OutboxEvent {}
