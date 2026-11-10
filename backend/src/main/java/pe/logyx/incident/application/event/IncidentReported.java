package pe.logyx.incident.application.event;

import pe.logyx.incident.domain.model.IncidentCategory;
import pe.logyx.incident.domain.model.Incident;

import java.util.UUID;

/** Evento de dominio: se reportó una incidencia (avisar a la otra parte y al operador). */
public record IncidentReported(UUID incidentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                               UUID reportedByOrgId, IncidentCategory category, String route) {}
