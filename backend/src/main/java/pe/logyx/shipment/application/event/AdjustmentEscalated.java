package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;
import pe.logyx.shipment.domain.model.AdjustmentType;

import java.math.BigDecimal;
import java.util.UUID;

/** Venció el plazo de la PYME para responder un ajuste (2.6): pasa al operador. Aviso al operador y a las partes. */
public record AdjustmentEscalated(UUID adjustmentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                                  AdjustmentType type, BigDecimal amount, String route, String code)
        implements OutboxEvent {}
