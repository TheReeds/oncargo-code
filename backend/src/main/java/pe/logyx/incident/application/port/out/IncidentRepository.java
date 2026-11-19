package pe.logyx.incident.application.port.out;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IncidentRepository {
    Optional<Incident> findById(UUID id);
    /** Más recientes primero. */
    List<Incident> listByShipment(UUID shipmentId);
    /**
     * Visibles (RLS) filtradas; {@code partyOrgId} null = sin filtro de organización (operador). Más recientes primero.
     */
    Paged<Incident> search(UUID partyOrgId, Collection<IncidentStatus> statuses, PageRequest page);
    /** RN11: envíos con una incidencia activa que congela (V23: las informativas no cuentan). */
    Set<UUID> shipmentsWithActive(Collection<UUID> shipmentIds);
    /** RN11: el envío tiene una activa que congela. */
    boolean hasActive(UUID shipmentId);
    boolean hasActiveOfCategory(UUID shipmentId, pe.logyx.incident.domain.model.IncidentCategory category);
    /** Bandeja del operador (V23): filtros por estado, severidad, responsable, SLA vencido y organización. */
    Paged<Incident> searchOperator(pe.logyx.incident.application.OperatorIncidentFilter filter, PageRequest page);
    void insertAttachment(Incident incident, UUID documentId);
    /** Documentos adjuntos en el alta, en orden de alta. */
    List<UUID> attachments(UUID incidentId);
    /** La persona es un usuario activo de una organización operadora (responsable de una incidencia). */
    boolean isOperatorProfile(UUID profileId);
    long countByStatuses(Collection<IncidentStatus> statuses);
    /** Inserta o actualiza. */
    void save(Incident incident);

    /** Más antiguos primero. */
    List<IncidentComment> listComments(UUID incidentId);
    void insertComment(IncidentComment comment);
}
