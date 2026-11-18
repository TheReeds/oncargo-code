package pe.logyx.operator.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    @ConfigProperty(name = "logyx.operator.sla", defaultValue = "PT24H")
    Duration sla;

    @ConfigProperty(name = "logyx.operator.auction-expiring-window", defaultValue = "PT10M")
    Duration expiringWindow;

    @Transactional
    public OperatorAlerts alerts() {
        OffsetDateTime now = OffsetDateTime.now();
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
        return new OperatorMetrics(
                marketplace.countRequests(ShipmentRequest.Status.PUBLISHED),
                marketplace.withoutBidsSince(OffsetDateTime.now().minus(sla)).size(),
                marketplace.countRequests(ShipmentRequest.Status.MATCHED),
                marketplace.countRequests(ShipmentRequest.Status.CANCELLED),
                s.pending(), s.inTransit(), s.delivered(), incidents.countActive(),
                organizations.countOfType(OrgType.PYME), organizations.countOfType(OrgType.CARRIER), s.gmv(), s.gmvDelivered());
    }

    /** Listado global paginado de solicitudes, más recientes primero. */
    @Transactional
    public Paged<RequestSummary> requests(OperatorRequestFilter filter, PageRequest page) {
        return marketplace.allRequests(filter, page);
    }

    /** Listado global paginado de envíos, más recientes primero. */
    @Transactional
    public Paged<ShipmentSummary> shipments(OperatorShipmentFilter filter, PageRequest page) {
        return shipments.allShipments(filter, page);
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

    public Paged<Organization> organizations(Organization.VerificationStatus status, OrgType type, PageRequest page) {
        return verification.search(status, type, page);
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
