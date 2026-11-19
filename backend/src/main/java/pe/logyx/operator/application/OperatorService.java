package pe.logyx.operator.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.marketplace.application.BidSummary;
import pe.logyx.marketplace.application.RequestSummaries;
import pe.logyx.negotiation.application.MessageSummary;
import pe.logyx.negotiation.application.NegotiationQueries;
import pe.logyx.negotiation.application.ThreadSummary;
import pe.logyx.operator.application.port.out.OperatorSearchReader;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.config.ParameterChange;
import pe.logyx.shared.config.ParameterValue;
import pe.logyx.shared.config.SystemParameters;
import pe.logyx.shared.config.domain.ParameterKey;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.Slice;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.incident.application.IncidentQueries;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.marketplace.application.AcceptOutcome;
import pe.logyx.marketplace.application.ManualAssignmentCommand;
import pe.logyx.marketplace.application.ManualAssignmentUseCase;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.application.OperatorRequestFilter;
import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shipment.application.OperatorShipmentFilter;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shipment.application.OperatorShipmentCounts;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Panel del operador LOGYX. Lecturas globales con las políticas RLS *_operator (V2); la asignación manual (RN10)
 * escribe con las políticas de V9. No usa SystemTx.
 */
@ApplicationScoped
public class OperatorService {

    @Inject MarketplaceQueries marketplace;
    @Inject ManualAssignmentUseCase manualAssignment;
    @Inject ShipmentQueries shipments;
    @Inject IncidentQueries incidents;
    @Inject FleetService fleet;
    @Inject OrganizationDirectory organizations;
    @Inject ReputationQueries reputation;
    @Inject pe.logyx.identity.application.OrganizationVerificationService verification;
    @Inject pe.logyx.identity.application.OrganizationGuard guard;
    @Inject AuditQueries audit;

    @Inject SystemParameters parameters;
    @Inject OperatorSearchReader search;
    @Inject RequestSummaries requestSummaries;
    @Inject NegotiationQueries negotiation;
    @Inject AuditService auditLog;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "logyx.operator.auction-expiring-window", defaultValue = "PT10M")
    Duration expiringWindow;

    @Transactional
    public OperatorAlerts alerts() {
        OffsetDateTime now = OffsetDateTime.now();
        Duration sla = parameters.assignmentSla();
        List<IncidentSummary> active = incidents.activeForOperator();
        Map<UUID, ShipmentSummary> byShipment = shipments.byIds(active.stream()
                        .map(i -> i.incident().shipmentId()).distinct().toList()).stream()
                .collect(Collectors.toMap(s -> s.shipment().id(), Function.identity()));
        return new OperatorAlerts(sla, expiringWindow, marketplace.withoutBidsSince(now.minus(sla)),
                marketplace.auctionsClosingBefore(now.plus(expiringWindow)), active, byShipment);
    }

    @Transactional
    public OperatorMetrics metrics() {
        OperatorShipmentCounts s = shipments.operatorCounts();
        Duration sla = parameters.assignmentSla();
        return new OperatorMetrics(
                marketplace.countRequests(ShipmentRequest.Status.PUBLISHED),
                marketplace.withoutBidsSince(OffsetDateTime.now().minus(sla)).size(),
                marketplace.countRequests(ShipmentRequest.Status.MATCHED),
                marketplace.countRequests(ShipmentRequest.Status.CANCELLED),
                s.pending(), s.inTransit(), s.delivered(), incidents.countActive(),
                organizations.countOfType(OrgType.PYME), organizations.countOfType(OrgType.CARRIER), s.gmv(), s.gmvDelivered(),
                sla.toHours(), parameters.incidentWarn().toHours(), parameters.incidentBreach().toHours(),
                parameters.commissionRate());
    }

    /** Listado global paginado de solicitudes, más recientes primero; {@code q} busca por texto (ver {@link OperatorSearchReader}). */
    @Transactional
    public Paged<RequestSummary> requests(OperatorRequestFilter filter, String q, PageRequest page) {
        String term = term(q);
        if (term == null) return marketplace.allRequests(filter, page);
        var hits = search.requests(term, filter, page);
        Map<UUID, ShipmentRequest> byId = marketplace.requestsByIds(hits.ids()).stream()
                .collect(Collectors.toMap(ShipmentRequest::id, Function.identity()));
        List<ShipmentRequest> ordered = hits.ids().stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return new Paged<>(requestSummaries.of(ordered, false), page, hits.total());
    }

    /** Listado global paginado de envíos, más recientes primero; {@code q} busca por texto. */
    @Transactional
    public Paged<ShipmentSummary> shipments(OperatorShipmentFilter filter, String q, PageRequest page) {
        String term = term(q);
        if (term == null) return shipments.allShipments(filter, page);
        var hits = search.shipments(term, filter, page);
        Map<UUID, ShipmentSummary> byId = shipments.byIds(hits.ids()).stream()
                .collect(Collectors.toMap(s -> s.shipment().id(), Function.identity()));
        return new Paged<>(hits.ids().stream().map(byId::get).filter(java.util.Objects::nonNull).toList(), page, hits.total());
    }

    /** Texto de búsqueda normalizado; {@code null} si no hay. Mínimo 2 caracteres, máximo 60 ({@code LOGYX-VAL-001}, {@code q}). */
    static String term(String q) {
        if (q == null || q.isBlank()) return null;
        String t = q.trim();
        if (t.length() < 2 || t.length() > 60) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La búsqueda admite entre 2 y 60 caracteres", "q");
        }
        return t;
    }

    // ----- Ofertas y chat de una solicitud (solo lectura; el chat queda auditado)

    /** Ofertas de la solicitud, de menor a mayor monto. Solo lectura. */
    @Transactional
    public List<BidSummary> bids(UUID requestId) {
        return marketplace.bidsFor(requestId);
    }

    /** Hilos de chat de la solicitud. Audita {@code OPERATOR_VIEWED_CHAT}. */
    @Transactional
    public List<ThreadSummary> threads(UUID requestId, String reason) {
        List<ThreadSummary> list = negotiation.threads(requestId);
        auditChat(requestId, "hilos", null, reason);
        return list;
    }

    /**
     * Mensajes de un hilo, solo lectura. Audita {@code OPERATOR_VIEWED_CHAT} al abrir el hilo (no al pedir páginas
     * anteriores con {@code before}).
     */
    @Transactional
    public Slice<MessageSummary> messages(UUID requestId, UUID carrierOrgId, UUID before, Integer size, String reason) {
        Slice<MessageSummary> s = negotiation.history(requestId, carrierOrgId, before, size);
        if (before == null) auditChat(requestId, "mensajes", carrierOrgId, reason);
        return s;
    }

    private void auditChat(UUID requestId, String what, UUID carrierOrgId, String reason) {
        String detail = what + (carrierOrgId == null ? "" : " del transportista " + carrierOrgId)
                + (reason == null || reason.isBlank() ? "" : " · motivo: " + reason.trim());
        auditLog.record(tenant.profileId(), tenant.orgId(), "OPERATOR_VIEWED_CHAT", "shipment_request", requestId,
                detail.length() > 500 ? detail.substring(0, 499) + "…" : detail);
    }

    // ----- Parámetros de sistema (V18)

    public List<ParameterValue> parameters() { return parameters.all(); }

    public ParameterValue updateParameter(ParameterKey key, String value, String reason, Integer expectedVersion) {
        return parameters.update(key, value, reason, expectedVersion);
    }

    public Paged<ParameterChange> parameterHistory(ParameterKey key, PageRequest page) {
        return parameters.history(key, page);
    }

    /**
     * Vehículos que pueden tomar la solicitud (RN02: disponibles, con capacidad, tipo apto para la carga y volumen; de
     * transportistas verificados, RN01), mejor Trust Score primero.
     */
    @Transactional
    public List<AssignmentCandidate> candidates(UUID requestId) {
        ShipmentRequest r = manualAssignment.assignableRequest(requestId);   // F29: 409 OPS-001 fuera de SLA o ya adjudicada
        List<Vehicle> vehicles = fleet.availableVehiclesForOperator(r.cargo().weightKg());
        var ids = vehicles.stream().map(Vehicle::organizationId).collect(Collectors.toSet());
        Map<UUID, Organization> carriers = organizations.byIds(ids);
        Map<UUID, BigDecimal> trust = reputation.trustScores(ids);
        var c = r.cargo();
        return vehicles.stream()
                .filter(v -> carriers.containsKey(v.organizationId())
                        && carriers.get(v.organizationId()).type() == OrgType.CARRIER
                        && guard.canTransact(carriers.get(v.organizationId())))                       // RN01
                .filter(v -> VehicleCargoCompatibility.canCarry(v.type(), c.cargoType())             // RN02 (F29)
                        && VehicleCargoCompatibility.volumeFits(v.maxVolumeM3(), c.volumeM3()))
                .map(v -> new AssignmentCandidate(carriers.get(v.organizationId()), trust.get(v.organizationId()), v))
                .sorted((a, b) -> b.carrierTrustScore().compareTo(a.carrierTrustScore()))
                .toList();
    }

    // ----- Verificación de organizaciones (RN01, F05): ver OrganizationVerificationService

    @Transactional
    public Paged<Organization> organizations(Organization.VerificationStatus status, OrgType type, String q,
                                             PageRequest page) {
        String term = term(q);
        if (term == null) return verification.search(status, type, page);
        var hits = search.organizations(term, status, type, page);
        Map<UUID, Organization> byId = organizations.byIds(hits.ids());
        return new Paged<>(hits.ids().stream().map(byId::get).filter(java.util.Objects::nonNull).toList(), page, hits.total());
    }

    public Organization verifyOrganization(UUID id, String note) { return verification.verify(id, note); }

    public Organization rejectOrganization(UUID id, String reason) { return verification.reject(id, reason); }

    public Organization suspendOrganization(UUID id, String reason) { return verification.suspend(id, reason); }

    // ----- Auditoría (F05): lectura de audit_log por la política audit_operator

    public Paged<AuditRecord> audit(AuditFilter filter, PageRequest page) { return audit.search(filter, page); }

    public List<String> auditActions() { return audit.actions(); }

    /** RN10: ver {@link ManualAssignmentUseCase}. */
    public AcceptOutcome assign(UUID requestId, ManualAssignmentCommand cmd) {
        return manualAssignment.assign(requestId, cmd);
    }
}
