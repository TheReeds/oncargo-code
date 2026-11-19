package pe.logyx.incident.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import pe.logyx.incident.domain.model.IncidentCategory;
import pe.logyx.incident.domain.model.Incident;

import java.util.UUID;

/**
 * Evento de dominio: se reportó una incidencia (avisar a la otra parte y al operador). V23: {@code blocking} (congela
 * el envío, RN11) y {@code severity}; nulos en eventos anteriores (se tratan como que congela).
 */
public record IncidentReported(UUID incidentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                               UUID reportedByOrgId, IncidentCategory category, String route, Boolean blocking,
                               String severity) implements OutboxEvent {

    public IncidentReported(UUID incidentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                            UUID reportedByOrgId, IncidentCategory category, String route) {
        this(incidentId, shipmentId, requesterOrgId, carrierOrgId, reportedByOrgId, category, route, null, null);
    }

    /** RN11: congela el avance de paradas. */
    public boolean freezes() { return blocking == null || blocking; }
}
