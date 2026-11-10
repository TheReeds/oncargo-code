package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.incident.application.event.IncidentReported;
import pe.logyx.incident.application.event.IncidentStatusChanged;
import pe.logyx.incident.application.port.out.IncidentRepository;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Reportar, comentar y (operador) cambiar el estado de incidencias (RF52–RF55, RN11). */
@ApplicationScoped
public class IncidentService {

    @Inject IncidentRepository incidents;
    @Inject IncidentQueries queries;
    @Inject ShipmentQueries shipments;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<IncidentReported> reported;
    @Inject Event<IncidentStatusChanged> statusChanged;

    /** Cualquier parte del envío (PYME, transportista o su conductor) reporta; congela el envío (RN11). */
    @Transactional
    public IncidentSummary report(UUID shipmentId, IncidentCategory category, String description) {
        ShipmentSummary sum = shipments.detail(shipmentId);   // 404 si no es parte (o conductor de otro envío)
        Shipment s = sum.shipment();
        UUID me = tenant.orgId();
        if (!s.isParty(me)) throw BusinessException.notFound("Envío");
        if (s.status() == Shipment.Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-INC-005", "El envío está cancelado");
        }
        if (incidents.hasActive(s.id())) {
            throw BusinessException.conflict("LOGYX-INC-001", "El envío ya tiene una incidencia activa");
        }
        Incident i = Incident.report(s.id(), s.requesterOrgId(), s.carrierOrgId(), me, tenant.profileId(), category,
                description);
        incidents.save(i);
        audit.record(tenant.profileId(), me, "INCIDENT_REPORTED", "incident", i.id(),
                category + " envio=" + s.id());
        reported.fire(new IncidentReported(i.id(), s.id(), s.requesterOrgId(), s.carrierOrgId(), me, category,
                sum.routeLabel()));
        return queries.summary(i, false);
    }

    @Transactional
    public IncidentComment comment(UUID incidentId, String body) {
        Incident i = queries.visible(incidentId);
        UUID me = tenant.orgId();
        if (!i.isParty(me) && tenant.orgType() != OrgType.OPERATOR) throw BusinessException.notFound("Incidencia");
        IncidentComment c = IncidentComment.write(i, me, tenant.profileId(), body);
        incidents.insertComment(c);
        return c;
    }

    /** Operador: mueve la incidencia (IN_REVIEW, RESOLVED, CLOSED). Al dejar de estar activa se libera el envío. */
    @Transactional
    public IncidentSummary changeStatus(UUID incidentId, IncidentStatus target, String resolutionNotes) {
        Incident i = incidents.findById(incidentId).orElseThrow(() -> BusinessException.notFound("Incidencia"));
        i.changeStatus(target, resolutionNotes, OffsetDateTime.now());
        incidents.save(i);
        audit.record(tenant.profileId(), tenant.orgId(), "INCIDENT_" + target, "incident", i.id(), null);
        statusChanged.fire(new IncidentStatusChanged(i.id(), i.shipmentId(), i.requesterOrgId(), i.carrierOrgId(),
                i.status(), i.resolutionNotes()));
        return queries.summary(i, true);
    }
}
