package pe.logyx.trip.application;

import pe.logyx.trip.domain.model.CarrierTrip;

import java.math.BigDecimal;

/**
 * Viaje con datos de presentación. {@code pendingBookings} y {@code confirmedBookings} solo se calculan para el
 * transportista dueño (RLS: la PYME no ve reservas ajenas); para otros son null.
 */
public record TripSummary(CarrierTrip trip, String carrierName, BigDecimal carrierTrustScore,
                          Long pendingBookings, Long confirmedBookings) {}
