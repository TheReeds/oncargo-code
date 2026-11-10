package pe.logyx.fleet.application;

import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;

public record AddVehicleCommand(String plate, Vehicle.Type type, BigDecimal maxWeightKg, BigDecimal maxVolumeM3) {}
