package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.incident.application.event.IncidentReported;
import pe.logyx.incident.application.event.IncidentStatusChanged;
import pe.logyx.incident.application.event.IncidentAssigned;
import pe.logyx.incident.application.port.out.IncidentRepository;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentLifecycleUseCase;
import pe.logyx.incident.domain.IncidentStatusPolicy;
import pe.logyx.incident.domain.model.IncidentShipmentOutcome;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Reportar, comentar y (operador) cambiar el estado de incidencias (RF52–RF55, RN11). */
@ApplicationScoped
public class IncidentService {

    @Inject IncidentRepository incidents;
    @Inject IncidentQueries queries;
    @Inject ShipmentQueries shipments;
    @Inject ShipmentLifecycleUseCase lifecycle;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<IncidentReported> reported;
    @Inject Event<IncidentStatusChanged> statusChanged;
    @Inject Event<IncidentAssigned> assigned;
    @Inject pe.logyx.shared.config.SystemParameters parameters;
    @Inject pe.logyx.shared.idempotency.IdempotencyKeys idempotency;
    @Inject pe.logyx.documents.application.DocumentQueries documents;

    static final String IDEMPOTENCY_SCOPE = "incident-report";
    static final int MAX_ATTACHMENTS = 5;

    /** Cualquier parte del envío (PYME, transportista o su conductor) reporta; congela el envío (RN11). */
    @Transactional
    public IncidentSummary report(UUID shipmentId, IncidentCategory category, String description) {
        return report(shipmentId, ReportIncidentCommand.simple(category, description));
    }

    /**
     * Alta v2 (2.7). Congela el envío solo si la categoría congela (RN11). Como mucho una activa que congela por envío
     * y una activa por categoría (LOGYX-INC-001). Adjuntos: documentos de ese envío que quien reporta ya ve
     * (LOGYX-INC-011). Con {@code idempotencyKey} un reintento (cola sin señal) devuelve la ya creada.
     */
    @Transactional
    public IncidentSummary report(UUID shipmentId, ReportIncidentCommand cmd) {
        String key = cmd.idempotencyKey();
        if (key != null) {
            idempotency.lock(IDEMPOTENCY_SCOPE, key);
            var previous = idempotency.find(IDEMPOTENCY_SCOPE, key);
            if (previous.isPresent()) {
                var existing = incidents.findById(previous.get());
                if (existing.isPresent() && existing.get().shipmentId().equals(shipmentId)) {
                    return queries.summary(existing.get(), false);
                }
            }
        }
        ShipmentSummary sum = shipments.detail(shipmentId);   // 404 si no es parte (o conductor de otro envío)
        Shipment s = sum.shipment();
        UUID me = tenant.orgId();
        if (!s.isParty(me)) throw BusinessException.notFound("Envío");
        if (s.status() == Shipment.Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-INC-005", "El envío está cancelado");
        }
        IncidentCategory category = cmd.category();
        if (category.blocking() && incidents.hasActive(s.id())) {
            throw BusinessException.conflict("LOGYX-INC-001", "El envío ya tiene una incidencia activa que lo detiene");
        }
        if (incidents.hasActiveOfCategory(s.id(), category)) {
            throw BusinessException.conflict("LOGYX-INC-001", "El envío ya tiene una incidencia activa de este tipo");
        }
        List<UUID> attachments = cmd.attachmentIds() == null ? List.of()
                : cmd.attachmentIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw BusinessException.badRequest("LOGYX-INC-011", "Adjunta hasta " + MAX_ATTACHMENTS + " archivos",
                    "attachmentIds");
        }
        for (UUID doc : attachments) {
            try {
                documents.ofShipment(s.id(), doc);
            } catch (BusinessException e) {
                throw BusinessException.badRequest("LOGYX-INC-011", "Los adjuntos deben ser documentos de este envío",
                        "attachmentIds");
            }
        }
        Incident.Location location = cmd.latitude() == null || cmd.longitude() == null ? null
                : new Incident.Location(cmd.latitude(), cmd.longitude());
        Incident i = Incident.report(s.id(), s.requesterOrgId(), s.carrierOrgId(), me, tenant.profileId(), category,
                cmd.description(), new Incident.Details(cmd.severity(), cmd.claimedAmount(), location,
                        deviceTime(cmd.occurredAt()), cmd.adjustmentId()),
                sev -> parameters.incidentSla(sev.name()));
        incidents.save(i);
        attachments.forEach(doc -> incidents.insertAttachment(i, doc));
        if (key != null) idempotency.remember(IDEMPOTENCY_SCOPE, key, i.id());
        audit.record(tenant.profileId(), me, "INCIDENT_REPORTED", "incident", i.id(),
                category + " " + i.severity() + (i.blocking() ? "" : " informativa") + " envio=" + s.id());
        reported.fire(new IncidentReported(i.id(), s.id(), s.requesterOrgId(), s.carrierOrgId(), me, category,
                sum.routeLabel(), i.blocking(), i.severity().name()));
        return queries.summary(i, false);
    }

    @Transactional
    public IncidentComment comment(UUID incidentId, String body) {
        return comment(incidentId, body, false);
    }

    /** {@code internal}: nota interna, solo el operador (LOGYX-INC-009); las partes no la ven (RLS). */
    @Transactional
    public IncidentComment comment(UUID incidentId, String body, boolean internal) {
        Incident i = queries.visible(incidentId);
        UUID me = tenant.orgId();
        if (!i.isParty(me) && tenant.orgType() != OrgType.OPERATOR) throw BusinessException.notFound("Incidencia");
        IncidentComment c = IncidentComment.write(i, me, tenant.profileId(), body, internal);
        incidents.insertComment(c);
        return c;
    }

    /** Quien la reportó retira una informativa activa (2.7, LOGYX-INC-007). */
    @Transactional
    public IncidentSummary withdraw(UUID incidentId) {
        Incident i = queries.visible(incidentId);
        i.withdraw(tenant.orgId(), OffsetDateTime.now());
        incidents.save(i);
        audit.record(tenant.profileId(), tenant.orgId(), "INCIDENT_WITHDRAWN", "incident", i.id(), null);
        statusChanged.fire(new IncidentStatusChanged(i.id(), i.shipmentId(), i.requesterOrgId(), i.carrierOrgId(),
                i.status(), i.resolutionNotes(), null));
        return queries.summary(i, true);
    }

    /**
     * Operador: reabre una resuelta o cerrada (2.7, LOGYX-INC-008) con motivo. Si congela y el envío está en curso,
     * vuelve a congelarlo (respetando una sola activa que congela por envío: LOGYX-INC-001).
     */
    @Transactional
    public IncidentSummary reopen(UUID incidentId, String reason) {
        Incident i = incidents.findById(incidentId).orElseThrow(() -> BusinessException.notFound("Incidencia"));
        i.ensureReopenable();
        if (i.blocking() && incidents.hasActive(i.shipmentId())) {
            throw BusinessException.conflict("LOGYX-INC-001", "El envío ya tiene otra incidencia activa que lo detiene");
        }
        if (incidents.hasActiveOfCategory(i.shipmentId(), i.category())) {
            throw BusinessException.conflict("LOGYX-INC-001", "El envío ya tiene una incidencia activa de este tipo");
        }
        i.reopen(reason, OffsetDateTime.now(), parameters.incidentSla(i.severity().name()));
        incidents.save(i);
        audit.record(tenant.profileId(), tenant.orgId(), "INCIDENT_REOPENED", "incident", i.id(),
                reason == null ? null : reason.strip());
        statusChanged.fire(new IncidentStatusChanged(i.id(), i.shipmentId(), i.requesterOrgId(), i.carrierOrgId(),
                i.status(), i.resolutionNotes(), null));
        return queries.summary(i, true);
    }

    /**
     * Operador: toma la incidencia ({@code assignee} null = yo) o la asigna a otra persona activa del operador
     * (LOGYX-INC-012); {@code unassign} la deja sin responsable. Avisa al responsable si no es quien asigna.
     */
    @Transactional
    public IncidentSummary assign(UUID incidentId, UUID assignee, boolean unassign) {
        Incident i = incidents.findById(incidentId).orElseThrow(() -> BusinessException.notFound("Incidencia"));
        UUID target = unassign ? null : assignee == null ? tenant.profileId() : assignee;
        if (target != null && !incidents.isOperatorProfile(target)) {
            throw BusinessException.badRequest("LOGYX-INC-012", "El responsable debe ser una persona del operador",
                    "assigneeProfileId");
        }
        i.assign(target, OffsetDateTime.now());
        incidents.save(i);
        audit.record(tenant.profileId(), tenant.orgId(), target == null ? "INCIDENT_UNASSIGNED" : "INCIDENT_ASSIGNED",
                "incident", i.id(), target == null ? null : "responsable=" + target);
        if (target != null && !target.equals(tenant.profileId())) {
            assigned.fire(new IncidentAssigned(i.id(), i.shipmentId(), tenant.orgId(), target, tenant.profileId(),
                    i.category().label()));
        }
        return queries.summary(i, true);
    }

    /** Hora del dispositivo de un reporte encolado: vale si es de las últimas 48 h y no futura (+2 min); si no, null. */
    static OffsetDateTime deviceTime(OffsetDateTime occurredAt) {
        if (occurredAt == null) return null;
        OffsetDateTime now = OffsetDateTime.now();
        if (occurredAt.isAfter(now.plusMinutes(2)) || occurredAt.isBefore(now.minusHours(48))) return null;
        return occurredAt;
    }

    @Transactional
    public IncidentSummary changeStatus(UUID incidentId, IncidentStatus target, String resolutionNotes) {
        return changeStatus(incidentId, target, resolutionNotes, null);
    }

    /**
     * Operador: mueve la incidencia (IN_REVIEW, RESOLVED, CLOSED). Al dejar de estar activa (se libera el envío, RN11)
     * exige motivo y el estado final del envío (RF55): RESUME (sigue), DELIVERED (se da por entregado: paradas
     * cerradas y flota liberada) o CANCELLED (se cancela con el motivo; la solicitud queda cancelada).
     */
    @Transactional
    public IncidentSummary changeStatus(UUID incidentId, IncidentStatus target, String resolutionNotes,
                                        IncidentShipmentOutcome outcome) {
        Incident i = incidents.findById(incidentId).orElseThrow(() -> BusinessException.notFound("Incidencia"));
        Shipment s = shipments.detail(i.shipmentId()).shipment();
        boolean deactivates = IncidentStatusPolicy.deactivates(i.status(), target);
        i.changeStatus(target, resolutionNotes, outcome, s.status(), OffsetDateTime.now());
        incidents.save(i);
        if (deactivates) {
            if (outcome == IncidentShipmentOutcome.CANCELLED && s.status() != Shipment.Status.CANCELLED) {
                lifecycle.cancelByOperator(s.id(), i.resolutionNotes());
            } else if (outcome == IncidentShipmentOutcome.DELIVERED && s.status() == Shipment.Status.IN_TRANSIT) {
                lifecycle.markDeliveredByOperator(s.id(), i.resolutionNotes());
            }
        }
        audit.record(tenant.profileId(), tenant.orgId(), "INCIDENT_" + target, "incident", i.id(),
                i.shipmentOutcome() == null ? null : "envio=" + i.shipmentOutcome());
        statusChanged.fire(new IncidentStatusChanged(i.id(), i.shipmentId(), i.requesterOrgId(), i.carrierOrgId(),
                i.status(), i.resolutionNotes(), deactivates ? outcome : null));
        return queries.summary(i, true);
    }
}
