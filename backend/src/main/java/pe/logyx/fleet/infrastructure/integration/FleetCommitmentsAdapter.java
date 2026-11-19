package pe.logyx.fleet.infrastructure.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.fleet.application.port.out.FleetCommitments;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.shipment.application.ResourceScheduler;
import pe.logyx.shipment.domain.ResourceSchedulePolicy.Commitment;
import pe.logyx.trip.application.TripQueries;

import java.util.List;
import java.util.UUID;

/** Implementa {@link FleetCommitments} con las lecturas de shipment, marketplace y trip (por su {@code application}). */
@ApplicationScoped
public class FleetCommitmentsAdapter implements FleetCommitments {

    @Inject ResourceScheduler schedule;
    @Inject MarketplaceQueries marketplace;
    @Inject TripQueries trips;

    @Override
    public List<ActiveShipment> activeShipmentsOfVehicle(UUID vehicleId) {
        return schedule.activeOfVehicle(vehicleId).stream().map(FleetCommitmentsAdapter::of).toList();
    }

    @Override
    public List<ActiveShipment> activeShipmentsOfDriver(UUID driverId) {
        return schedule.activeOfDriver(driverId).stream().map(FleetCommitmentsAdapter::of).toList();
    }

    @Override
    public int pendingBidsOfVehicle(UUID vehicleId) {
        return marketplace.pendingBidsOfVehicle(vehicleId);
    }

    @Override
    public int openTripsOfVehicle(UUID vehicleId) {
        return (int) trips.openTripsOfVehicle(vehicleId);
    }

    private static ActiveShipment of(Commitment c) {
        return new ActiveShipment(c.shipmentId(), c.window().from(), c.window().to());
    }
}
