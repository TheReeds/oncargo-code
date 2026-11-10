package pe.logyx.incident.application.event;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.Incident;

import java.util.UUID;

/** Evento de dominio: el operador cambió el estado de una incidencia. */
public record IncidentStatusChanged(UUID incidentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                                    IncidentStatus status, String resolutionNotes) {}
