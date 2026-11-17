package pe.logyx.marketplace.application;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code loadGroupId}: plan Smart Load que agrupa esta oferta con otras en el mismo vehículo (F12), o null. */
public record PlaceBidCommand(UUID vehicleId, BigDecimal amount, String notes, UUID loadGroupId) {
    public PlaceBidCommand(UUID vehicleId, BigDecimal amount, String notes) {
        this(vehicleId, amount, notes, null);
    }
}
