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
    Set<UUID> shipmentsWithActive(Collection<UUID> shipmentIds);
    boolean hasActive(UUID shipmentId);
    long countByStatuses(Collection<IncidentStatus> statuses);
    /** Inserta o actualiza. */
    void save(Incident incident);

    /** Más antiguos primero. */
    List<IncidentComment> listComments(UUID incidentId);
    void insertComment(IncidentComment comment);
}
