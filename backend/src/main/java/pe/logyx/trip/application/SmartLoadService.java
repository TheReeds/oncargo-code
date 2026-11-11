package pe.logyx.trip.application;

import pe.logyx.shared.tenancy.SystemTx;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.trip.application.event.SmartLoadAccepted;
import pe.logyx.trip.application.event.SmartLoadSuggested;
import pe.logyx.trip.application.port.out.SmartLoadRepository;
import pe.logyx.trip.domain.SmartLoadPlanner;
import pe.logyx.trip.domain.model.SmartLoadStatus;
import pe.logyx.trip.domain.model.SmartLoadSuggestion;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Load Planner (OE07): genera, lista y resuelve sugerencias. {@link #generateForCurrentCarrier()} corre bajo RLS
 * (el transportista autenticado); {@link #generateForAllCarriers()} lo invoca el job programado sin usuario (rol dueño),
 * porque por definición recorre a todos los transportistas.
 */
@ApplicationScoped
public class SmartLoadService {

    @Inject SystemTx systemTx;

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MAX_REQUESTS_SCANNED = 300;

    @Inject SmartLoadRepository suggestions;
    @Inject FleetService fleet;
    @Inject MarketplaceQueries marketplace;
    @Inject OrganizationDirectory organizations;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<SmartLoadSuggested> suggested;
    @Inject Event<SmartLoadAccepted> accepted;

    @ConfigProperty(name = "logyx.smartload.ttl", defaultValue = "PT4H")
    Duration ttl;

    @Transactional
    public List<SmartLoadSuggestion> generateForCurrentCarrier() {
        UUID me = tenant.orgId();
        return generate(me, fleet.vehicles(), marketplace.openRequestsForPlanning(MAX_REQUESTS_SCANNED));
    }

    /** Job programado: recorre los vehículos disponibles de todos los transportistas. Sin RLS (sin usuario). */
    // SystemTx: job sin usuario; recorre los vehículos de todos los transportistas.
    public int generateForAllCarriers() {
        return systemTx.call(() -> {
            List<ShipmentRequest> requests = marketplace.openRequestsForPlanning(MAX_REQUESTS_SCANNED);
            Map<UUID, List<Vehicle>> byCarrier = fleet.availableVehicles().stream()
                    .collect(Collectors.groupingBy(Vehicle::organizationId));
            int created = 0;
            for (var e : byCarrier.entrySet()) {
                created += generate(e.getKey(), e.getValue(), requests).size();
            }
            if (created > 0) Log.infof("Smart Load: %d sugerencias nuevas", created);
            return created;
        });
    }

    @Transactional
    public List<SmartLoadSuggestion> list(SmartLoadStatus status) {
        return suggestions.listByCarrier(tenant.orgId(), status);
    }

    /**
     * Aceptar marca la sugerencia y avisa a las PYMEs agrupadas (RF33). No adjudica ni crea ofertas: el transportista
     * debe ofertar en cada solicitud desde el marketplace (la subasta inversa sigue mandando).
     */
    @Transactional
    public SmartLoadSuggestion accept(UUID id) {
        SmartLoadSuggestion s = own(id);
        OffsetDateTime now = OffsetDateTime.now();
        s.accept(now);
        var ids = s.items().stream().map(SmartLoadSuggestion.Item::requestId).toList();
        long stillOpen = marketplace.requestsByIds(ids).stream()
                .filter(r -> r.status() == ShipmentRequest.Status.PUBLISHED).count();
        if (stillOpen < ids.size()) {
            throw BusinessException.conflict("LOGYX-TRP-011", "Alguna de las cargas de la sugerencia ya no está disponible");
        }
        suggestions.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "SMART_LOAD_ACCEPTED", "smart_load_suggestion", s.id(), null);
        String carrierName = organizations.get(s.carrierOrgId()).legalName();
        accepted.fire(new SmartLoadAccepted(s.id(), s.carrierOrgId(), carrierName, s.items().stream()
                .map(i -> new SmartLoadAccepted.Item(i.requestId(), i.requesterOrgId(),
                        i.originCity().label + " → " + i.destinationCity().label)).toList()));
        return s;
    }

    @Transactional
    public SmartLoadSuggestion reject(UUID id) {
        SmartLoadSuggestion s = own(id);
        s.reject(OffsetDateTime.now());
        suggestions.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "SMART_LOAD_REJECTED", "smart_load_suggestion", s.id(), null);
        return s;
    }

    private SmartLoadSuggestion own(UUID id) {
        return suggestions.findOwned(id, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Sugerencia"));
    }

    private List<SmartLoadSuggestion> generate(UUID carrierOrgId, List<Vehicle> vehicles, List<ShipmentRequest> requests) {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = LocalDate.now(LIMA);
        List<SmartLoadSuggestion> live = suggestions.listByCarrier(carrierOrgId, SmartLoadStatus.PENDING).stream()
                .filter(s -> !s.isExpired(now)).toList();
        Set<UUID> busyVehicles = live.stream().map(SmartLoadSuggestion::vehicleId).collect(Collectors.toSet());
        Set<UUID> busyRequests = live.stream().flatMap(s -> s.items().stream())
                .map(SmartLoadSuggestion.Item::requestId).collect(Collectors.toSet());

        List<SmartLoadPlanner.VehicleSlot> slots = vehicles.stream()
                .filter(v -> v.isAvailable() && !busyVehicles.contains(v.id()))
                .map(v -> new SmartLoadPlanner.VehicleSlot(v.id(), v.type(), v.plate(), v.maxWeightKg())).toList();
        List<SmartLoadPlanner.Load> loads = requests.stream()
                .filter(ShipmentRequest::isPublished)
                .filter(r -> !r.cargo().pickupDate().isBefore(today))
                .filter(r -> !busyRequests.contains(r.id()))
                .map(r -> new SmartLoadPlanner.Load(r.id(), r.requesterOrgId(), r.cargo().originCity(),
                        r.cargo().destinationCity(), r.cargo().weightKg(), r.cargo().pickupDate(),
                        r.pricing().suggestedPrice(), r.cargo().cargoType())).toList();
        if (slots.isEmpty() || loads.size() < 2) return List.of();

        List<SmartLoadSuggestion> created = new ArrayList<>();
        for (SmartLoadPlanner.Plan plan : SmartLoadPlanner.plan(slots, loads)) {
            SmartLoadSuggestion s = SmartLoadSuggestion.from(carrierOrgId, plan, now, ttl);
            suggestions.save(s);
            created.add(s);
            suggested.fire(new SmartLoadSuggested(s.id(), carrierOrgId, s.vehiclePlate(), s.items().size(),
                    s.occupancyPct(), s.combinedIncome()));
        }
        return created;
    }
}
