package pe.logyx.trip.application.event;

import java.math.BigDecimal;
import java.util.UUID;

/** El transportista confirmó la reserva y se generó el envío: avisa a la PYME. */
public record TripBookingConfirmed(UUID bookingId, UUID tripId, UUID shipmentId, UUID carrierOrgId,
                                   UUID requesterOrgId, String carrierName, String route, BigDecimal price) {}
