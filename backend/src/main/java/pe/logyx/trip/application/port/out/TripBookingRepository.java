package pe.logyx.trip.application.port.out;

import pe.logyx.trip.domain.model.TripBooking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripBookingRepository {
    /** Visible para el usuario actual (RLS: las dos partes y el operador). */
    Optional<TripBooking> findById(UUID id);
    /** Más antiguas primero. */
    List<TripBooking> listByTrip(UUID tripId);
    /** PENDING o CONFIRMED del viaje. */
    List<TripBooking> listActiveByTrip(UUID tripId);
    /** Más recientes primero. */
    List<TripBooking> listByRequester(UUID requesterOrgId);
    long countPendingByCarrier(UUID carrierOrgId);
    long countActiveByRequester(UUID requesterOrgId);
    /** La reserva que generó ese envío, si existe. */
    Optional<TripBooking> findByShipment(UUID shipmentId);
    /** Inserta o actualiza. */
    void save(TripBooking booking);
}
