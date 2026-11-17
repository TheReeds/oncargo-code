package pe.logyx.trip.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.shared.outbox.Outboxed;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.trip.application.port.out.CarrierTripRepository;
import pe.logyx.trip.application.port.out.TripBookingRepository;

import java.time.OffsetDateTime;

/**
 * F06: si se cancela un envío generado por una reserva confirmada (lo cancela una parte o el operador, no la reserva),
 * la reserva queda cancelada con el mismo motivo y el viaje recupera la capacidad. Llega por el outbox y corre en la
 * transacción de sistema del despacho (la PYME no puede escribir el viaje del transportista). Idempotente: si la
 * reserva ya no está activa (p. ej. la canceló la propia PYME), no hace nada.
 */
@ApplicationScoped
public class BookingShipmentObserver {

    @Inject TripBookingRepository bookings;
    @Inject CarrierTripRepository trips;

    void onShipmentStatus(@Observes @Outboxed ShipmentStatusChanged e) {
        if (e.status() != Shipment.Status.CANCELLED) return;
        bookings.findByShipment(e.shipmentId()).filter(b -> b.isConfirmed()).ifPresent(b -> {
            b.cancel(e.reason() == null ? "Envío cancelado" : "Envío cancelado: " + e.reason(), OffsetDateTime.now());
            bookings.save(b);
            trips.findByIdForUpdate(b.tripId()).ifPresent(t -> {
                t.releaseBooking(b.weightKg());
                trips.save(t);
            });
        });
    }
}
