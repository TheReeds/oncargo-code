package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.AdjustmentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un ajuste se aceptó, rechazó o retiró (2.6). {@code byOperator}: lo decidió el operador tras escalarse. Aviso a la
 * contraparte de quien decidió (y a las dos si fue el operador).
 */
public record AdjustmentDecided(UUID adjustmentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                                AdjustmentType type, BigDecimal amount, AdjustmentStatus status, boolean byOperator,
                                String note, String route, String code) implements OutboxEvent {}
