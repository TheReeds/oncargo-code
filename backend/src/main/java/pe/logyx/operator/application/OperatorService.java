package pe.logyx.operator.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.incident.application.IncidentQueries;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.marketplace.application.AcceptOutcome;
import pe.logyx.marketplace.application.ManualAssignmentCommand;
import pe.logyx.marketplace.application.ManualAssignmentUseCase;
import pe.logyx.marketplace.application.MarketplaceQueries;
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
                organizations.countOfType(OrgType.PYME), organizations.countOfType(OrgType.CARRIER));
    }

    /** Vehículos que pueden tomar la solicitud (disponibles y con capacidad, RN02), mejor Trust Score primero. */
    @Transactional
    public List<AssignmentCandidate> candidates(UUID requestId) {
        ShipmentRequest r = marketplace.requestForParticipant(requestId);
        List<Vehicle> vehicles = fleet.availableVehiclesForOperator(r.cargo().weightKg());
        var ids = vehicles.stream().map(Vehicle::organizationId).collect(Collectors.toSet());
        Map<UUID, Organization> carriers = organizations.byIds(ids);
        Map<UUID, BigDecimal> trust = reputation.trustScores(ids);
        return vehicles.stream()
                .filter(v -> carriers.containsKey(v.organizationId())
                        && carriers.get(v.organizationId()).type() == OrgType.CARRIER)
                .map(v -> new AssignmentCandidate(carriers.get(v.organizationId()), trust.get(v.organizationId()), v))
                .sorted((a, b) -> b.carrierTrustScore().compareTo(a.carrierTrustScore()))
                .toList();
    }

    /** RN10: ver {@link ManualAssignmentUseCase}. */
    public AcceptOutcome assign(UUID requestId, ManualAssignmentCommand cmd) {
        return manualAssignment.assign(requestId, cmd);
    }
}
