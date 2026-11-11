package pe.logyx.trip.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/** La PYME canceló su reserva: avisa al transportista. */
public record TripBookingCancelled(UUID bookingId, UUID tripId, UUID carrierOrgId, UUID requesterOrgId,
                                   String requesterName, String route, boolean wasConfirmed) implements OutboxEvent {}
