package pe.logyx.trip.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.trip.application.port.out.TripBookingRepository;
import pe.logyx.trip.domain.model.BookingStatus;
import pe.logyx.trip.domain.model.TripBooking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheTripBookingRepository implements TripBookingRepository {

    @Override
    public Optional<TripBooking> findById(UUID id) {
        return TripBookingEntity.<TripBookingEntity>findByIdOptional(id).map(TripBookingEntity::toDomain);
    }

    @Override
    public List<TripBooking> listByTrip(UUID tripId) {
        return map(TripBookingEntity.list("tripId = ?1 order by createdAt asc, id", tripId));
    }

    @Override
    public List<TripBooking> listActiveByTrip(UUID tripId) {
        return map(TripBookingEntity.list("tripId = ?1 and status in ?2 order by createdAt asc, id", tripId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)));
    }

    @Override
    public List<TripBooking> listByRequester(UUID requesterOrgId) {
        return map(TripBookingEntity.list("requesterOrgId = ?1 order by createdAt desc, id", requesterOrgId));
    }

    @Override
    public long countPendingByCarrier(UUID carrierOrgId) {
        return TripBookingEntity.count("carrierOrgId = ?1 and status = ?2", carrierOrgId, BookingStatus.PENDING);
    }

    @Override
    public long countActiveByRequester(UUID requesterOrgId) {
        return TripBookingEntity.count("requesterOrgId = ?1 and status in ?2", requesterOrgId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
    }

    @Override
    public void save(TripBooking booking) {
        TripBookingEntity e = TripBookingEntity.findById(booking.id());
        if (e == null) {
            e = new TripBookingEntity();
            e.apply(booking);
            e.persist();
        } else {
            e.apply(booking);
        }
    }

    private static List<TripBooking> map(List<TripBookingEntity> list) {
        return list.stream().map(TripBookingEntity::toDomain).toList();
    }
}
