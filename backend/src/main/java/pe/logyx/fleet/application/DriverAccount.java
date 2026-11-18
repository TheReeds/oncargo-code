package pe.logyx.fleet.application;

import pe.logyx.fleet.domain.model.Driver;

/** Conductor + datos de su cuenta (nombre, email y si la cuenta está activa vienen de identity). */
public record DriverAccount(Driver driver, String fullName, String email, boolean active) {}
