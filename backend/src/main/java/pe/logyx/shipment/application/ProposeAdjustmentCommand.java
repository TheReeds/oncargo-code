package pe.logyx.shipment.application;

import pe.logyx.shipment.domain.model.AdjustmentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Propuesta de ajuste (2.6). {@code stopId}, {@code evidenceDocumentId} e {@code idempotencyKey} opcionales (con la
 * clave, un reintento de la cola del conductor devuelve el ajuste ya creado).
 */
public record ProposeAdjustmentCommand(AdjustmentType type, BigDecimal amount, String note, UUID stopId,
                                       UUID evidenceDocumentId, String idempotencyKey) {

    public ProposeAdjustmentCommand(AdjustmentType type, BigDecimal amount, String note, UUID stopId,
                                    UUID evidenceDocumentId) {
        this(type, amount, note, stopId, evidenceDocumentId, null);
    }
}
