package pe.logyx.shipment.application.port.out;

import java.util.UUID;

/**
 * Evidencia de entrega (RN06/RF37): si la parada tiene al menos una foto subida. Lo implementa
 * {@code infrastructure/integration} con el módulo documents (documents ya depende de shipment).
 */
public interface DeliveryEvidenceProvider {
    boolean hasDeliveryPhoto(UUID shipmentId, UUID stopId);

    /** Roadmap 2.5: la parada de recojo tiene al menos una foto de la carga ({@code PICKUP_PHOTO}). */
    default boolean hasPickupPhoto(UUID shipmentId, UUID stopId) { return false; }

    /** 2.6/2.7: el documento es de ese envío y quien llama lo ve (evidencia de un ajuste, adjunto de incidencia). */
    default boolean belongsToShipment(UUID shipmentId, UUID documentId) { return false; }
}
