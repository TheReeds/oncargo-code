package pe.logyx.shipment.infrastructure.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.documents.application.DocumentQueries;
import pe.logyx.shipment.application.port.out.DeliveryEvidenceProvider;

import java.util.UUID;

/** Adaptador del puerto de evidencia de entrega sobre el módulo documents. */
@ApplicationScoped
public class DocumentsEvidenceProvider implements DeliveryEvidenceProvider {

    @Inject DocumentQueries documents;

    @Override
    public boolean hasDeliveryPhoto(UUID shipmentId, UUID stopId) {
        return documents.hasDeliveryPhoto(shipmentId, stopId);
    }

    @Override
    public boolean belongsToShipment(UUID shipmentId, UUID documentId) {
        try {
            documents.ofShipment(shipmentId, documentId);
            return true;
        } catch (pe.logyx.shared.domain.BusinessException e) {
            return false;
        }
    }

    @Override
    public boolean hasPickupPhoto(UUID shipmentId, UUID stopId) {
        return documents.hasPickupPhoto(shipmentId, stopId);
    }
}
