package pe.logyx.alerts.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.alerts.application.port.out.SavedSearchRepository;
import pe.logyx.alerts.domain.SavedSearchMatcher;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
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
 */
@ApplicationScoped
public class LoadAlertMatcher {

    /** Una persona a avisar y las búsquedas suyas que coincidieron (para nombrarlas). */
    public record Match(UUID organizationId, UUID profileId, List<String> searchNames) {}

    @Inject SavedSearchRepository searches;
    @Inject FleetService fleet;
    @Inject VehicleAgenda agenda;
    @Inject MarketplaceQueries marketplace;

    @ConfigProperty(name = "logyx.alerts.max-per-search-per-hour", defaultValue = "10")
    int maxPerHour;

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

    private boolean canBid(SavedSearch s, List<Vehicle> vehicles, UUID requestId) {
        List<Vehicle> pool = s.vehicleId() == null ? vehicles
                : vehicles.stream().filter(v -> v.id().equals(s.vehicleId())).toList();
        return pool.stream().anyMatch(v -> agenda.assessPublished(v, requestId).map(VehicleAgenda.Assessment::eligible).orElse(false));
    }
}
