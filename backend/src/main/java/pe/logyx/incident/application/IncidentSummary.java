package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Incidencia con los nombres de las organizaciones involucradas y (en el detalle) su hilo de comentarios. */
public record IncidentSummary(Incident incident, Map<UUID, String> orgNames, List<IncidentComment> comments) {

    public String name(UUID orgId) { return orgNames.get(orgId); }
}
