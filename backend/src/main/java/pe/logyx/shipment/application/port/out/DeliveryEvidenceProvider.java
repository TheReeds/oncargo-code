package pe.logyx.shipment.application.port.out;

import java.util.UUID;

/**
 * Evidencia de entrega (RN06/RF37): si la parada tiene al menos una foto subida. Lo implementa
 * {@code infrastructure/integration} con el módulo documents (documents ya depende de shipment).
 */
public interface DeliveryEvidenceProvider {
    boolean hasDeliveryPhoto(UUID shipmentId, UUID stopId);
}
