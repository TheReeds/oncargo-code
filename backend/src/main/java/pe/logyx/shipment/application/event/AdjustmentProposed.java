package pe.logyx.shipment.application.event;

import pe.logyx.shared.outbox.OutboxEvent;
import pe.logyx.shipment.domain.model.AdjustmentType;

import java.math.BigDecimal;
import java.util.UUID;

/** El transportista propuso un ajuste del precio (2.6). Aviso a la PYME. Formato persistido: agrega campos, no renombres. */
public record AdjustmentProposed(UUID adjustmentId, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId,
                                 AdjustmentType type, BigDecimal amount, String route, String code)
        implements OutboxEvent {}
