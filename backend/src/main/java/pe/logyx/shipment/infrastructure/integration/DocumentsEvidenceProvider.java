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
}
