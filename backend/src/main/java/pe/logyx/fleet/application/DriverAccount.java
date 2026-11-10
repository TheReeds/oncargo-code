package pe.logyx.fleet.application;

import pe.logyx.fleet.domain.model.Driver;

/** Conductor + datos de su cuenta (nombre y email vienen de identity). */
public record DriverAccount(Driver driver, String fullName, String email) {}
