package pe.logyx.incident.domain.model;

import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Comentario en el hilo de una incidencia (partes del envío y operador). */
public record IncidentComment(UUID id, UUID incidentId, UUID requesterOrgId, UUID carrierOrgId, UUID authorOrgId,
                              UUID authorProfileId, String body, OffsetDateTime createdAt) {

    public static IncidentComment write(Incident incident, UUID authorOrgId, UUID authorProfileId, String body) {
        if (incident.status() == IncidentStatus.CLOSED) {
            throw BusinessException.conflict("LOGYX-INC-004", "La incidencia está cerrada");
        }
        return new IncidentComment(UUID.randomUUID(), incident.id(), incident.requesterOrgId(),
                incident.carrierOrgId(), authorOrgId, authorProfileId, body.trim(), OffsetDateTime.now());
    }
}
