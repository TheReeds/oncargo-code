package pe.logyx.alerts.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.alerts.application.port.out.RouteAlertLog;
import pe.logyx.alerts.application.port.out.SavedSearchRepository;
import pe.logyx.alerts.domain.RouteInterestMatcher;
import pe.logyx.alerts.domain.SavedSearchMatcher;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationPreferencesService;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.application.VehicleAgenda;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Al publicarse una solicitud (2.3) la evalúa contra las búsquedas guardadas activas de todos los transportistas:
 * <ol>
 *   <li>filtros de la búsqueda ({@link SavedSearchMatcher});</li>
 *   <li>que pueda ofertar: con {@code vehicleId} ese vehículo; sin él, algún vehículo activo de la flota, con la misma
 *       regla de ofertar ({@link VehicleAgenda}: RN02 por fecha, agenda, documentos);</li>
 *   <li>límite de {@code logyx.alerts.max-per-search-per-hour} avisos por búsqueda y hora;</li>
 *   <li>un solo aviso por persona y carga aunque coincidan varias búsquedas suyas (nombra la primera).</li>
 * </ol>
 * Corre en la transacción de sistema del despacho del outbox (cruza organizaciones, sin usuario): lee todas las
 * búsquedas y escribe la bitácora {@code saved_search_alerts}, que además hace idempotente el reintento del evento.
 *
 * <p><b>Alertas implícitas (V24, 2.2):</b> {@link #routeMatchesFor} hace lo mismo con las rutas de interés que el
 * transportista declaró en las preferencias de su organización ({@code route_alerts = true};
 * {@link RouteInterestMatcher}): misma regla de vehículo, sin bloqueados, tope
 * {@code logyx.alerts.max-implicit-per-org-per-hour} por organización y bitácora {@code org_route_alerts}. Quien ya
 * recibió el aviso por una búsqueda guardada no lo recibe dos veces (lo resuelve el observador de avisos).
 */
@ApplicationScoped
public class LoadAlertMatcher {

    /** Una persona a avisar y las búsquedas suyas que coincidieron (para nombrarlas). */
    public record Match(UUID organizationId, UUID profileId, List<String> searchNames) {}

    /** Organización transportista a la que le interesa la carga por sus rutas de interés. */
    public record RouteMatch(UUID organizationId) {}

    @Inject SavedSearchRepository searches;
    @Inject FleetService fleet;
    @Inject VehicleAgenda agenda;
    @Inject MarketplaceQueries marketplace;
    @Inject OrganizationPreferencesService organizationPreferences;
    @Inject RouteAlertLog routeAlerts;

    @ConfigProperty(name = "logyx.alerts.max-per-search-per-hour", defaultValue = "10")
    int maxPerHour;

    @ConfigProperty(name = "logyx.alerts.max-implicit-per-org-per-hour", defaultValue = "20")
    int maxImplicitPerHour;

    /** A quién avisar de la solicitud publicada (vacío si ya no está publicada o nadie coincide). */
    public List<Match> matchesFor(UUID requestId) {
        Optional<ShipmentRequest> found = marketplace.publishedForSystem(requestId);
        if (found.isEmpty()) return List.of();
        ShipmentRequest r = found.get();
        var c = r.cargo();
        var load = new SavedSearchMatcher.Load(c.originCity(), c.destinationCity(), c.cargoType(), c.weightKg(), c.pickupDate());
        var blocked = searches.blockedCarriersOf(r.requesterOrgId());                  // 2.9: un bloqueado no ve esas cargas
        List<SavedSearch> candidates = searches.activeForOrigin(c.originCity()).stream()
                .filter(s -> !s.organizationId().equals(r.requesterOrgId()) && !blocked.contains(s.organizationId()))
                .filter(s -> SavedSearchMatcher.matches(s, load)).toList();
        if (candidates.isEmpty()) return List.of();

        Map<UUID, Long> sent = searches.alertsSince(candidates.stream().map(SavedSearch::id).toList(),
                OffsetDateTime.now().minusHours(1));
        Map<UUID, List<Vehicle>> fleets = new HashMap<>();
        Map<UUID, Match> byProfile = new LinkedHashMap<>();
        Map<UUID, List<String>> names = new HashMap<>();
        for (SavedSearch s : candidates) {
            if (!SavedSearchMatcher.withinHourlyLimit(sent.getOrDefault(s.id(), 0L), maxPerHour)) continue;
            List<Vehicle> vehicles = fleets.computeIfAbsent(s.organizationId(), fleet::vehiclesOf);
            if (!canBid(s, vehicles, requestId)) continue;
            if (!searches.recordAlert(s.id(), requestId)) continue;                       // ya avisado (reintento)
            names.computeIfAbsent(s.profileId(), k -> new ArrayList<>()).add(s.name());
            byProfile.putIfAbsent(s.profileId(), new Match(s.organizationId(), s.profileId(), null));
        }
        return byProfile.values().stream()
                .map(m -> new Match(m.organizationId(), m.profileId(), List.copyOf(names.get(m.profileId())))).toList();
    }

    /** Organizaciones a avisar por sus rutas de interés (vacío si ya no está publicada o nadie coincide). */
    public List<RouteMatch> routeMatchesFor(UUID requestId) {
        Optional<ShipmentRequest> found = marketplace.publishedForSystem(requestId);
        if (found.isEmpty()) return List.of();
        ShipmentRequest r = found.get();
        var c = r.cargo();
        var pricing = r.pricing();
        var load = new RouteInterestMatcher.Load(c.originCity(), c.destinationCity(), c.cargoType(),
                pricing == null ? null : pricing.suggestedPrice(), pricing == null ? null : pricing.distanceKm());
        var candidates = organizationPreferences.carriersWatchingRoute(c.originCity(), c.destinationCity());
        if (candidates.isEmpty()) return List.of();
        var blocked = searches.blockedCarriersOf(r.requesterOrgId());
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);
        List<RouteMatch> out = new ArrayList<>();
        for (var p : candidates) {
            UUID org = p.organizationId();
            if (org.equals(r.requesterOrgId()) || blocked.contains(org)) continue;
            var interest = new RouteInterestMatcher.Interest(
                    p.routes().stream().map(x -> new RouteInterestMatcher.Route(x.origin(), x.destination()))
                            .collect(java.util.stream.Collectors.toSet()),
                    java.util.Set.copyOf(p.cargoTypes()), p.minPricePerKm());
            if (!RouteInterestMatcher.matches(interest, load)) continue;
            if (!RouteInterestMatcher.withinHourlyLimit(routeAlerts.sentSince(org, since), maxImplicitPerHour)) continue;
            List<Vehicle> vehicles = fleet.vehiclesOf(org);
            boolean canBid = vehicles.stream().anyMatch(v -> agenda.assessPublished(v, requestId)
                    .map(VehicleAgenda.Assessment::eligible).orElse(false));
            if (!canBid) continue;
            if (!routeAlerts.record(org, requestId)) continue;                       // ya avisada (reintento)
            out.add(new RouteMatch(org));
        }
        return out;
    }

    private boolean canBid(SavedSearch s, List<Vehicle> vehicles, UUID requestId) {
        List<Vehicle> pool = s.vehicleId() == null ? vehicles
                : vehicles.stream().filter(v -> v.id().equals(s.vehicleId())).toList();
        return pool.stream().anyMatch(v -> agenda.assessPublished(v, requestId).map(VehicleAgenda.Assessment::eligible).orElse(false));
    }
}
