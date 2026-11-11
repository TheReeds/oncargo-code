package pe.logyx.trip.application.event;

import java.util.List;
import java.util.UUID;

/** El transportista canceló el viaje: avisa a las PYMEs con reservas pendientes o confirmadas (ya canceladas). */
public record TripCancelled(UUID tripId, UUID carrierOrgId, String carrierName, String route,
                            List<Affected> affected) {
    public record Affected(UUID bookingId, UUID requesterOrgId, boolean wasConfirmed) {}
}
