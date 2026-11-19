package pe.logyx.fleet.application;

import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;

/** Corrección de un vehículo (la placa no cambia). Nulos en tipo y kg = no se tocan; el volumen siempre se aplica. */
public record UpdateVehicleCommand(Vehicle.Type type, BigDecimal maxWeightKg, BigDecimal maxVolumeM3) {}
