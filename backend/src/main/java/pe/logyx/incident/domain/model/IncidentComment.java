package pe.logyx.incident.domain.model;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Comentario en el hilo de una incidencia (partes del envío y operador). {@code internal} (V23): nota interna del
 * operador; solo el operador la escribe y la lee (también lo exige la RLS de {@code incident_comments}).
 */
public record IncidentComment(UUID id, UUID incidentId, UUID requesterOrgId, UUID carrierOrgId, UUID authorOrgId,
                              UUID authorProfileId, String body, OffsetDateTime createdAt, boolean internal) {

    public IncidentComment(UUID id, UUID incidentId, UUID requesterOrgId, UUID carrierOrgId, UUID authorOrgId,
                           UUID authorProfileId, String body, OffsetDateTime createdAt) {
        this(id, incidentId, requesterOrgId, carrierOrgId, authorOrgId, authorProfileId, body, createdAt, false);
    }

    /** RN07: los comentarios de las partes los lee la contraparte; no admiten datos de contacto ({@code body}). */
    public static IncidentComment write(Incident incident, UUID authorOrgId, UUID authorProfileId, String body) {
        return write(incident, authorOrgId, authorProfileId, body, false);
    }

    /** {@code internal}: solo el operador (LOGYX-INC-009 si una parte lo intenta). */
    public static IncidentComment write(Incident incident, UUID authorOrgId, UUID authorProfileId, String body,
                                        boolean internal) {
        if (incident.status() == IncidentStatus.CLOSED) {
            throw BusinessException.conflict("LOGYX-INC-004", "La incidencia está cerrada");
        }
        boolean party = incident.isParty(authorOrgId);
        if (internal && party) {
            throw new BusinessException(403, "LOGYX-INC-009", "Las notas internas son solo del operador");
        }
        String clean = party ? ContactInfoPolicy.clean(body, "body") : body.trim();
        return new IncidentComment(UUID.randomUUID(), incident.id(), incident.requesterOrgId(),
                incident.carrierOrgId(), authorOrgId, authorProfileId, clean, OffsetDateTime.now(), internal);
    }
}
