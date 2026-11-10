package pe.logyx.marketplace.application;

import java.math.BigDecimal;
import java.util.UUID;

/** RN10: el operador asigna un transportista y uno de sus vehículos; {@code amount} null = precio sugerido. */
public record ManualAssignmentCommand(UUID carrierOrgId, UUID vehicleId, BigDecimal amount, String notes) {}
