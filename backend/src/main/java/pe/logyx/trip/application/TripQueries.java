package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.trip.application.port.out.CarrierTripRepository;
import pe.logyx.trip.application.port.out.SmartLoadRepository;
import pe.logyx.trip.application.port.out.TripBookingRepository;
import pe.logyx.trip.domain.model.BookingStatus;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Lecturas de viajes y reservas (todo bajo RLS). */
@ApplicationScoped
@Transactional
public class TripQueries {

    private static final int TOP_RETURN_LOADS = 3;

    @Inject CarrierTripRepository trips;
    @Inject TripBookingRepository bookings;
    @Inject SmartLoadRepository smartLoads;
    @Inject ReturnLoadService returnLoads;
    @Inject TripSummaries summaries;
    @Inject TenantContext tenant;

    // ----- Transportista

    public List<TripSummary> myTrips() {
        return summaries.trips(trips.listByCarrier(tenant.orgId()), true);
    }

    public Paged<TripSummary> myTrips(PageRequest page) {
        Paged<CarrierTrip> p = Paged.slice(trips.listByCarrier(tenant.orgId()), page);
        return new Paged<>(summaries.trips(p.items(), true), page, p.total());
    }

    /** Reservas del viaje (solo el transportista dueño). */
    public List<BookingSummary> bookingsOfTrip(UUID tripId) {
        trips.findOwned(tripId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Viaje"));
        return summaries.bookings(bookings.listByTrip(tripId));
    }

    // ----- PYME

    /** Viajes OPEN/FILLING con salida futura y espacio suficiente. */
    public Paged<TripSummary> search(TripFilter filter, PageRequest page) {
        Paged<CarrierTrip> p = trips.searchOpen(filter, OffsetDateTime.now(), page);
        return new Paged<>(summaries.trips(p.items(), false), page, p.total());
    }

    public List<BookingSummary> myBookings(BookingStatus status) {
        var list = bookings.listByRequester(tenant.orgId()).stream()
                .filter(b -> status == null || b.status() == status).toList();
        return summaries.bookings(list);
    }

    // ----- Común

    public TripSummary detail(UUID tripId) {
        CarrierTrip t = trips.findById(tripId).orElseThrow(() -> BusinessException.notFound("Viaje"));
        return summaries.trips(List.of(t), t.isOwnedBy(tenant.orgId())).getFirst();
    }

    // ----- Dashboard

    public TripDashboard dashboard() {
        UUID org = tenant.orgId();
        if (tenant.orgType() == OrgType.CARRIER) {
            List<ReturnLoadMatch> returns = returnLoads.forCurrentCarrier();
            return new TripDashboard(
                    trips.countByCarrierAndStatus(org, List.of(TripStatus.OPEN, TripStatus.FILLING)),
                    bookings.countPendingByCarrier(org),
                    smartLoads.countPending(org, OffsetDateTime.now()),
                    (long) returns.size(),
                    returns.stream().limit(TOP_RETURN_LOADS).toList(), null);
        }
        return new TripDashboard(null, null, null, null, null, bookings.countActiveByRequester(org));
    }
}
