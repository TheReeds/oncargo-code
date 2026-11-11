package pe.logyx.trip.application;

import pe.logyx.catalog.domain.CargoType;

import java.math.BigDecimal;

public record BookTripCommand(BigDecimal weightKg, String cargoDescription, CargoType cargoType,
                              String pickupAddress, String deliveryAddress) {}
