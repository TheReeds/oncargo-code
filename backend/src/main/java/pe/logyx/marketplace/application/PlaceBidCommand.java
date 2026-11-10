package pe.logyx.marketplace.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBidCommand(UUID vehicleId, BigDecimal amount, String notes) {}
