package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.IncidentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.incident.application.port.out.IncidentRepository;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;

import java.util.*;
import java.util.stream.Collectors;

/** Lecturas de incidencias. RLS: partes del envío y operador; el conductor, solo las de su envío. */
@ApplicationScoped
@Transactional
public class IncidentQueries {

    @Inject IncidentRepository incidents;
    @Inject ShipmentQueries shipments;
    @Inject OrganizationDirectory organizations;
    @Inject TenantContext tenant;

    public List<IncidentSummary> byShipment(UUID shipmentId) {
        shipments.detail(shipmentId);
        return summaries(incidents.listByShipment(shipmentId));
    }

    /** PYME/transportista: las de sus envíos. Operador: todas. {@code statuses} vacío = todos los estados. */
    public Paged<IncidentSummary> search(Collection<IncidentStatus> statuses, PageRequest page) {
        UUID party = tenant.orgType() == OrgType.OPERATOR ? null : tenant.orgId();
        Paged<Incident> p = incidents.search(party, statuses, page);
        return new Paged<>(summaries(p.items()), page, p.total());
    }

    public IncidentSummary detail(UUID id) {
        return summary(visible(id), true);
    }

    /** Puerto RN11 de shipment: envíos con incidencia activa (bajo la RLS del usuario actual). */
    public Set<UUID> shipmentsWithActiveIncident(Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Set.of();
        return incidents.shipmentsWithActive(shipmentIds);
    }

    /** Métrica del operador. */
    public long countActive() {
        return incidents.countByStatuses(List.of(IncidentStatus.OPEN, IncidentStatus.IN_REVIEW));
    }

    /** Activas de toda la plataforma (operador), las más antiguas primero. */
    public List<IncidentSummary> activeForOperator() {
        List<Incident> list = new ArrayList<>(incidents.search(null,
                List.of(IncidentStatus.OPEN, IncidentStatus.IN_REVIEW), new PageRequest(0, PageRequest.MAX_SIZE)).items());
        list.sort(Comparator.comparing(Incident::createdAt));
        return summaries(list);
    }

    public Incident visible(UUID id) {
        Incident i = incidents.findById(id).orElseThrow(() -> BusinessException.notFound("Incidencia"));
        if (tenant.isDriver()) shipments.detail(i.shipmentId());   // el conductor solo ve las de su envío
        return i;
    }

    public IncidentSummary summary(Incident i, boolean withComments) {
        List<IncidentComment> comments = withComments ? incidents.listComments(i.id()) : List.of();
        Set<UUID> orgIds = new HashSet<>(List.of(i.requesterOrgId(), i.carrierOrgId(), i.reportedByOrgId()));
        comments.forEach(c -> orgIds.add(c.authorOrgId()));
        return new IncidentSummary(i, names(orgIds), comments);
    }

    private List<IncidentSummary> summaries(List<Incident> list) {
        if (list.isEmpty()) return List.of();
        Set<UUID> orgIds = new HashSet<>();
        list.forEach(i -> { orgIds.add(i.requesterOrgId()); orgIds.add(i.carrierOrgId()); });
        Map<UUID, String> names = names(orgIds);
        return list.stream().map(i -> new IncidentSummary(i, names, List.of())).toList();
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        return organizations.byIds(ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().legalName()));
    }
}
