package pe.logyx.marketplace.application;

/** Aviso que acompaña a una oferta aceptada (no la bloquea): {@code LOGYX-CAP-006} = el vehículo ya tiene ofertas solapadas. */
public record BidWarning(String code, String message) {}
