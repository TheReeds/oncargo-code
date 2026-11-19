package pe.logyx.marketplace.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Mejora de la oferta (2.8): {@code amount} nuevo y menor (o nulo = mismo) y/o {@code vehicleId} distinto (o nulo = mismo). */
public record ImproveBidCommand(BigDecimal amount, UUID vehicleId) {}
