package pe.logyx.incident.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.Incident;

import java.util.UUID;

/**
 * Evento de dominio: el operador cambió el estado de una incidencia. {@code shipmentOutcome} (V13): estado final del
 * envío fijado al resolverla (null si sigue activa o en eventos anteriores).
 */
public record IncidentStatusChanged(UUID incidentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                                    IncidentStatus status, String resolutionNotes,
                                    pe.logyx.incident.domain.model.IncidentShipmentOutcome shipmentOutcome)
        implements OutboxEvent {

    public IncidentStatusChanged(UUID incidentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                                 IncidentStatus status, String resolutionNotes) {
        this(incidentId, shipmentId, requesterOrgId, carrierOrgId, status, resolutionNotes, null);
    }
}
