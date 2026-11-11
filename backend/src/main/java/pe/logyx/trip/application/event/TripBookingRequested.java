package pe.logyx.trip.application.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Una PYME pidió espacio en un viaje: avisa al transportista. */
public record TripBookingRequested(UUID bookingId, UUID tripId, UUID carrierOrgId, UUID requesterOrgId,
                                   String requesterName, String route, BigDecimal weightKg, BigDecimal price) {}
