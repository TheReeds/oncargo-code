package pe.logyx.trip.application;

import pe.logyx.trip.domain.model.TripPricingMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublishTripCommand(String originCity, String destinationCity, OffsetDateTime departureAt, UUID vehicleId,
                                 BigDecimal capacityKg, TripPricingMode pricingMode, BigDecimal rate, String notes) {}
