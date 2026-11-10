package pe.logyx.incident.infrastructure.persistence;

import pe.logyx.incident.domain.model.IncidentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.incident.application.port.out.IncidentRepository;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheIncidentRepository implements IncidentRepository {

    private static final List<IncidentStatus> ACTIVE = List.of(IncidentStatus.OPEN, IncidentStatus.IN_REVIEW);

    @Override
    public Optional<Incident> findById(UUID id) {
        return IncidentEntity.<IncidentEntity>findByIdOptional(id).map(IncidentEntity::toDomain);
    }

    @Override
    public List<Incident> listByShipment(UUID shipmentId) {
        return map(IncidentEntity.list("shipmentId = ?1 order by createdAt desc", shipmentId));
    }

    @Override
    public Paged<Incident> search(UUID partyOrgId, Collection<IncidentStatus> statuses, PageRequest page) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (partyOrgId != null) {
            params.add(partyOrgId);
            where.add("(requesterOrgId = ?" + params.size() + " or carrierOrgId = ?" + params.size() + ")");
        }
        if (statuses != null && !statuses.isEmpty()) {
            params.add(statuses);
            where.add("status in ?" + params.size());
        }
        String q = where.isEmpty() ? "1 = 1" : String.join(" and ", where);
        var query = IncidentEntity.<IncidentEntity>find(q + " order by createdAt desc, id", params.toArray())
                .page(page.page(), page.size());
        return new Paged<>(map(query.list()), page, IncidentEntity.count(q, params.toArray()));
    }

    @Override
    public Set<UUID> shipmentsWithActive(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Set.of();
        return IncidentEntity.<IncidentEntity>list("shipmentId in ?1 and status in ?2", shipmentIds, ACTIVE).stream()
                .map(e -> e.shipmentId).collect(Collectors.toSet());
    }

    @Override
    public boolean hasActive(UUID shipmentId) {
        return IncidentEntity.count("shipmentId = ?1 and status in ?2", shipmentId, ACTIVE) > 0;
    }

    @Override
    public long countByStatuses(Collection<IncidentStatus> statuses) {
        return IncidentEntity.count("status in ?1", statuses);
    }

    @Override
    public void save(Incident incident) {
        IncidentEntity e = IncidentEntity.findById(incident.id());
        if (e == null) {
            e = new IncidentEntity();
            e.apply(incident);
            e.persist();
        } else {
            e.apply(incident);
        }
    }

    @Override
    public List<IncidentComment> listComments(UUID incidentId) {
        return IncidentCommentEntity.<IncidentCommentEntity>list("incidentId = ?1 order by createdAt, id", incidentId)
                .stream().map(IncidentCommentEntity::toDomain).toList();
    }

    @Override
    public void insertComment(IncidentComment comment) {
        IncidentCommentEntity.from(comment).persist();
    }

    private static List<Incident> map(List<IncidentEntity> list) {
        return list.stream().map(IncidentEntity::toDomain).toList();
    }
}
