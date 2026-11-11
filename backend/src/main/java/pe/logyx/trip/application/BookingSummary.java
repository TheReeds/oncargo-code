package pe.logyx.trip.application;

import pe.logyx.trip.domain.model.TripBooking;

public record BookingSummary(TripBooking booking, String requesterName, String carrierName) {}
