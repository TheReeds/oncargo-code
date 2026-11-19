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

    /** RN11 (V23): solo las activas que congelan. */
    @Override
    public Set<UUID> shipmentsWithActive(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Set.of();
        return IncidentEntity.<IncidentEntity>list("shipmentId in ?1 and status in ?2 and blocking = true",
                        shipmentIds, ACTIVE).stream()
                .map(e -> e.shipmentId).collect(Collectors.toSet());
    }

    /** RN11 (V23): hay una activa que congela. */
    @Override
    public boolean hasActive(UUID shipmentId) {
        return IncidentEntity.count("shipmentId = ?1 and status in ?2 and blocking = true", shipmentId, ACTIVE) > 0;
    }

    @Override
    public boolean hasActiveOfCategory(UUID shipmentId, pe.logyx.incident.domain.model.IncidentCategory category) {
        return IncidentEntity.count("shipmentId = ?1 and status in ?2 and category = ?3", shipmentId, ACTIVE,
                category) > 0;
    }

    @Override
    public Paged<Incident> searchOperator(pe.logyx.incident.application.OperatorIncidentFilter f, PageRequest page) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            params.add(f.statuses());
            where.add("status in ?" + params.size());
        }
        if (f.severities() != null && !f.severities().isEmpty()) {
            params.add(f.severities());
            where.add("severity in ?" + params.size());
        }
        if (f.assigneeProfileId() != null) {
            params.add(f.assigneeProfileId());
            where.add("assigneeProfileId = ?" + params.size());
        }
        if (f.unassigned()) where.add("assigneeProfileId is null");
        if (f.breachedAt() != null) {
            params.add(f.breachedAt());
            where.add("dueAt < ?" + params.size());
        }
        if (f.organizationId() != null) {
            params.add(f.organizationId());
            where.add("(requesterOrgId = ?" + params.size() + " or carrierOrgId = ?" + params.size() + ")");
        }
        String q = where.isEmpty() ? "1 = 1" : String.join(" and ", where);
        String order = f.byDueDate() ? " order by dueAt, createdAt" : " order by createdAt desc, id";
        var query = IncidentEntity.<IncidentEntity>find(q + order, params.toArray()).page(page.page(), page.size());
        return new Paged<>(map(query.list()), page, IncidentEntity.count(q, params.toArray()));
    }

    @Override
    public void insertAttachment(Incident incident, UUID documentId) {
        IncidentEntity.getEntityManager().flush();   // la FK exige que la incidencia ya esté insertada
        IncidentEntity.getEntityManager().createNativeQuery(
                        "INSERT INTO incident_attachments (incident_id, document_id, requester_org_id, carrier_org_id) "
                                + "VALUES (?1, ?2, ?3, ?4)")
                .setParameter(1, incident.id()).setParameter(2, documentId)
                .setParameter(3, incident.requesterOrgId()).setParameter(4, incident.carrierOrgId()).executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UUID> attachments(UUID incidentId) {
        return IncidentEntity.getEntityManager().createNativeQuery(
                        "SELECT document_id FROM incident_attachments WHERE incident_id = ?1 ORDER BY created_at, document_id",
                        UUID.class)
                .setParameter(1, incidentId).getResultList();
    }

    @Override
    public boolean isOperatorProfile(UUID profileId) {
        Number n = (Number) IncidentEntity.getEntityManager().createNativeQuery(
                        "SELECT count(*) FROM profiles p JOIN organizations o ON o.id = p.organization_id "
                                + "WHERE p.id = ?1 AND p.active AND o.type = 'OPERATOR'")
                .setParameter(1, profileId).getSingleResult();
        return n.longValue() > 0;
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
