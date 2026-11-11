package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.trip.application.event.TripBookingRejected;
import pe.logyx.trip.application.port.out.TripBookingRepository;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripBooking;

import java.time.OffsetDateTime;

/** Rechaza las reservas aún pendientes de un viaje que dejó de admitir reservas (cerrado, lleno o ya salió). */
@ApplicationScoped
public class BookingSweeper {

    @Inject TripBookingRepository bookings;
    @Inject OrganizationDirectory organizations;
    @Inject Event<TripBookingRejected> rejected;

    @Transactional(Transactional.TxType.MANDATORY)
    public int rejectPending(CarrierTrip trip, String reason) {
        var pending = bookings.listByTrip(trip.id()).stream().filter(TripBooking::isPending).toList();
        if (pending.isEmpty()) return 0;
        String carrierName = organizations.get(trip.carrierOrgId()).legalName();
        OffsetDateTime now = OffsetDateTime.now();
        for (TripBooking b : pending) {
            b.reject(reason, now);
            bookings.save(b);
            rejected.fire(new TripBookingRejected(b.id(), trip.id(), trip.carrierOrgId(), b.requesterOrgId(),
                    carrierName, trip.routeLabel(), reason));
        }
        return pending.size();
    }
}
