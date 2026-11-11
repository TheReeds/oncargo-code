package pe.logyx.trip.application.event;

import java.util.UUID;

/** El transportista rechazó la reserva (o el viaje se cerró con la reserva pendiente): avisa a la PYME. */
public record TripBookingRejected(UUID bookingId, UUID tripId, UUID carrierOrgId, UUID requesterOrgId,
                                  String carrierName, String route, String reason) {}
