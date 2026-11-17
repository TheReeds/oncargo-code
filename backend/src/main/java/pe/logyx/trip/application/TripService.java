package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.CancelShipmentUseCase;
import pe.logyx.trip.application.event.TripCancelled;
import pe.logyx.trip.application.port.out.CarrierTripRepository;
import pe.logyx.trip.application.port.out.TripBookingRepository;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripBooking;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** El transportista publica, cierra y cancela sus viajes (RN12). */
@ApplicationScoped
public class TripService {

    static final String CANCEL_NOTE = "Viaje cancelado por el transportista";
    static final String CLOSE_NOTE = "El viaje se cerró sin confirmar esta reserva";

    @Inject CarrierTripRepository trips;
    @Inject TripBookingRepository bookings;
    @Inject FleetService fleet;
    @Inject CancelShipmentUseCase shipments;
    @Inject BookingSweeper sweeper;
    @Inject TripSummaries summaries;
    @Inject OrganizationDirectory organizations;
    @Inject pe.logyx.identity.application.OrganizationGuard guard;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<TripCancelled> cancelled;

    @Transactional
    public TripSummary publish(PublishTripCommand cmd) {
        City origin = City.require(cmd.originCity());
        City destination = City.require(cmd.destinationCity());
        guard.ensureCanTransact(tenant.orgId());                                   // RN01 (transportista verificado)
        Vehicle vehicle = fleet.ownVehicle(cmd.vehicleId());                       // 404 si no es suyo
        CarrierTrip trip = CarrierTrip.publish(tenant.orgId(), vehicle, origin, destination, cmd.departureAt(),
                cmd.capacityKg(), cmd.pricingMode(), cmd.rate(), cmd.notes(), OffsetDateTime.now());   // RN12
        trips.save(trip);
        audit.record(tenant.profileId(), tenant.orgId(), "TRIP_PUBLISHED", "carrier_trip", trip.id(),
                trip.routeLabel() + " " + trip.capacityKg() + " kg");
        return summaries.trips(List.of(trip), true).getFirst();
    }

    /** Cierre manual: no admite más reservas; las pendientes se rechazan. */
    @Transactional
    public TripSummary close(UUID tripId) {
        CarrierTrip trip = locked(tripId);
        trip.close();
        sweeper.rejectPending(trip, CLOSE_NOTE);
        trips.save(trip);
        audit.record(tenant.profileId(), tenant.orgId(), "TRIP_CLOSED", "carrier_trip", trip.id(), null);
        return summaries.trips(List.of(trip), true).getFirst();
    }

    /**
     * Cancela el viaje y sus reservas pendientes/confirmadas. Los envíos de reservas confirmadas que aún no salieron se
     * cancelan; si alguno ya salió, 409 LOGYX-TRP-007 y no se cambia nada.
     */
    @Transactional
    public TripSummary cancel(UUID tripId) {
        CarrierTrip trip = locked(tripId);
        trip.cancel();
        List<TripBooking> active = bookings.listActiveByTrip(trip.id());
        for (TripBooking b : active) {
            if (b.isConfirmed() && b.shipmentId() != null && shipments.hasStarted(b.shipmentId())) {
                throw BusinessException.conflict("LOGYX-TRP-007",
                        "El viaje tiene envíos que ya salieron; no se puede cancelar");
            }
        }
        List<TripCancelled.Affected> affected = active.stream()
                .map(b -> new TripCancelled.Affected(b.id(), b.requesterOrgId(), b.isConfirmed())).toList();
        OffsetDateTime now = OffsetDateTime.now();
        for (TripBooking b : active) {
            if (b.isConfirmed() && b.shipmentId() != null) shipments.cancelPending(b.shipmentId());
            b.cancel(CANCEL_NOTE, now);
            bookings.save(b);
        }
        trips.save(trip);
        audit.record(tenant.profileId(), tenant.orgId(), "TRIP_CANCELLED", "carrier_trip", trip.id(),
                "reservas=" + active.size());
        cancelled.fire(new TripCancelled(trip.id(), trip.carrierOrgId(),
                organizations.get(trip.carrierOrgId()).legalName(), trip.routeLabel(), affected));
        return summaries.trips(List.of(trip), true).getFirst();
    }

    private CarrierTrip locked(UUID tripId) {
        trips.findOwned(tripId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Viaje"));
        return trips.findByIdForUpdate(tripId).orElseThrow(() -> BusinessException.notFound("Viaje"));
    }
}
