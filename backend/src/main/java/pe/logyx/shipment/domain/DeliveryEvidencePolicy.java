package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.ShipmentStop;

/**
 * RF37/RN06: la parada de entrega solo se completa si el conductor subió antes al menos una foto de evidencia
 * ({@code POST /shipments/{id}/documents} con {@code type=DELIVERY_PHOTO} y el {@code stopId} de la entrega).
 * Sin esa foto tampoco se podría calificar el envío.
 */
public final class DeliveryEvidencePolicy {

    private DeliveryEvidencePolicy() {}

    public static void ensureEvidence(ShipmentStop.Type stopType, boolean hasDeliveryPhoto) {
        if (stopType == ShipmentStop.Type.DELIVERY && !hasDeliveryPhoto) {
            throw BusinessException.conflict("LOGYX-TRK-006",
                    "Sube al menos una foto de evidencia de la entrega antes de completarla (RN06)");
        }
    }
}
