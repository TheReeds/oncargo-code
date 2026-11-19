package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.routing.application.RoutePlanner;
import pe.logyx.shipment.domain.ResourceSchedulePolicy;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.trip.domain.CityProximityPolicy;
import pe.logyx.trip.domain.ReturnLoadPolicy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cargas de retorno (RF61–RF63) del transportista actual. Todo bajo RLS: sus envíos y el marketplace público. */
@ApplicationScoped
@Transactional
public class ReturnLoadService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MAX_REQUESTS_SCANNED = 300;

    @Inject ShipmentQueries shipments;
    @Inject MarketplaceQueries marketplace;
    @Inject FleetService fleet;
    @Inject RoutePlanner routes;

    public List<ReturnLoadMatch> forCurrentCarrier() {
        LocalDate today = LocalDate.now(LIMA);
        Map<UUID, Vehicle> vehicles = fleet.vehicles().stream().collect(Collectors.toMap(Vehicle::id, Function.identity()));
        List<ReturnLoadPolicy.Leg> legs = new ArrayList<>();
        for (ShipmentSummary s : shipments.mine()) {
            Shipment sh = s.shipment();
            boolean delivered = sh.status() == Shipment.Status.DELIVERED;
            boolean active = sh.status() == Shipment.Status.IN_TRANSIT || sh.status() == Shipment.Status.PENDING;
            if ((!delivered && !active) || s.request() == null) continue;
            Vehicle v = vehicles.get(sh.vehicleId());
            if (v == null) continue;
            // RN02 por fecha: un envío no entregado libera el vehículo tras su ventana (recojo + ⌈km/500⌉ días,
            // ResourceSchedulePolicy): el retorno se busca desde el día siguiente, no desde hoy.
            LocalDate from = delivered && sh.deliveredAt() != null ? sh.deliveredAt().atZoneSameInstant(LIMA).toLocalDate()
                    : ResourceSchedulePolicy.window(s.request().pickupDate(), s.request().distanceKm(),
                            today).to().plusDays(1);
            legs.add(new ReturnLoadPolicy.Leg(sh.id(), v.id(), v.plate(), v.type(), v.maxWeightKg(),
                    s.request().destinationCity(), delivered, from));
        }
        if (legs.isEmpty()) return List.of();

        Map<UUID, RequestSummary> byId = marketplace.openRequests(MAX_REQUESTS_SCANNED).stream()
                .collect(Collectors.toMap(r -> r.request().id(), Function.identity()));
        List<ReturnLoadPolicy.Candidate> candidates = byId.values().stream().map(r -> {
            var c = r.request().cargo();
            return new ReturnLoadPolicy.Candidate(r.request().id(), c.originCity(), c.destinationCity(), c.weightKg(),
                    c.pickupDate(), c.cargoType());
        }).toList();
        return ReturnLoadPolicy.match(legs, candidates, today, new CityProximityPolicy(routes::distanceKm)).stream()
                .map(m -> new ReturnLoadMatch(m, byId.get(m.candidate().requestId()))).toList();
    }
}
