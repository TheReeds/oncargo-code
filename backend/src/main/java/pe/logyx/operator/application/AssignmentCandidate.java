package pe.logyx.operator.application;

import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.domain.model.Organization;

import java.math.BigDecimal;

/** Vehículo disponible con capacidad suficiente para la asignación manual (RN10), con su transportista. */
public record AssignmentCandidate(Organization carrier, BigDecimal carrierTrustScore, Vehicle vehicle) {}
