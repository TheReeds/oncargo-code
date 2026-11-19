package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Incidencia con los nombres de las organizaciones involucradas y (en el detalle) su hilo de comentarios, sus adjuntos,
 * el valor declarado de la carga (de la solicitud) y el nombre del responsable del operador.
 */
public record IncidentSummary(Incident incident, Map<UUID, String> orgNames, List<IncidentComment> comments,
                              List<Attachment> attachments, BigDecimal declaredValue, String assigneeName) {

    /** Documento adjunto en el alta (se descarga por {@code /documents/{id}/file}). */
    public record Attachment(UUID documentId, String fileName, String type, String contentType) {}

    public IncidentSummary(Incident incident, Map<UUID, String> orgNames, List<IncidentComment> comments) {
        this(incident, orgNames, comments, List.of(), null, null);
    }

    public String name(UUID orgId) { return orgNames.get(orgId); }
}
