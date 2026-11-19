package pe.logyx.fleet.infrastructure.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.application.port.out.FleetPlanning;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.application.VehicleAgenda;
import pe.logyx.routing.application.RoutePlanner;
import pe.logyx.shipment.application.ResourceScheduler;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.ResourceSchedulePolicy.Window;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.trip.application.TripQueries;
import pe.logyx.trip.domain.model.TripStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Implementa {@link FleetPlanning} con las lecturas de shipment, marketplace, trip y routing (por su {@code application}). */
@ApplicationScoped
public class FleetPlanningAdapter implements FleetPlanning {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject ShipmentQueries shipments;
    @Inject ResourceScheduler schedule;
    @Inject MarketplaceQueries marketplace;
    @Inject VehicleAgenda agenda;
    @Inject TripQueries trips;
    @Inject FleetService fleet;
    @Inject RoutePlanner routes;

    @Override
    public List<Slot> slots(Collection<UUID> vehicleIds) {
        List<Slot> out = new ArrayList<>();
        for (ShipmentSummary s : shipments.mine()) {
            if (!isActive(s.shipment()) || s.request() == null) continue;
            Window w = schedule.window(s.request().pickupDate(), s.request().distanceKm());
            out.add(new Slot(Kind.SHIPMENT, s.shipment().id(), s.routeLabel(), w.from(), w.to(), s.shipment().vehicleId(),
                    s.shipment().driverId(), s.request().destinationCity()));
        }
        for (UUID vehicleId : vehicleIds) {
            for (var b : agenda.pendingBidSlots(vehicleId)) {
                out.add(new Slot(Kind.BID, b.refId(), b.label(), b.from(), b.to(), vehicleId, null, null));
            }
        }
        for (var t : trips.myTrips()) {
            var trip = t.trip();
            if (trip.status() != TripStatus.OPEN && trip.status() != TripStatus.FILLING) continue;
            LocalDate day = trip.departureAt().atZoneSameInstant(LIMA).toLocalDate();
            out.add(new Slot(Kind.TRIP, trip.id(), trip.routeLabel(), day, day, trip.vehicleId(), null, trip.destinationCity()));
        }
        return out;
    }

    @Override
    public Optional<ShipmentPlan> shipmentPlan(UUID shipmentId) {
        ShipmentSummary s;
        try {
            s = shipments.detail(shipmentId);
        } catch (pe.logyx.shared.domain.BusinessException e) {
            return Optional.empty();
        }
        if (s.request() == null) return Optional.empty();
        Window w = schedule.window(s.request().pickupDate(), s.request().distanceKm());
        var r = s.request();
        return Optional.of(new ShipmentPlan(s.shipment().id(), s.routeLabel(), r.originCity(), r.destinationCity(),
                r.cargoType(), r.weightKg(), w.from(), w.to(), s.shipment().vehicleId(), s.shipment().driverId(),
                isActive(s.shipment())));
    }

    @Override
    public VehicleFit vehicleFit(UUID shipmentId, UUID vehicleId) {
        ShipmentSummary s = shipments.detail(shipmentId);
        Vehicle v = fleet.ownVehicle(vehicleId);
        var request = marketplace.requestForParticipant(s.shipment().requestId());
        var a = agenda.assess(v, request, s.shipment().loadGroupId());
        return new VehicleFit(a.eligible(), a.blocker() == null ? null : a.blocker().code(),
                a.blocker() == null ? null : a.blocker().getMessage());
    }

    @Override
    public double distanceKm(City from, City to) {
        return routes.distanceKm(from, to);
    }

    private static boolean isActive(Shipment s) {
        return s.status() == Shipment.Status.PENDING || s.status() == Shipment.Status.IN_TRANSIT;
    }
}
