package pe.logyx.documents.application;

import pe.logyx.documents.domain.model.DocumentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.documents.application.port.out.DocumentRepository;
import pe.logyx.documents.application.port.out.DocumentStorage;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Lecturas de documentos. Visibilidad: RLS (partes del envío y operador) + regla del conductor vía shipment. */
@ApplicationScoped
@Transactional
public class DocumentQueries {

    /** Archivo listo para descargar. */
    public record Download(Document document, InputStream content) {}

    @Inject DocumentRepository documents;
    @Inject DocumentStorage storage;
    @Inject ShipmentQueries shipments;
    @Inject TenantContext tenant;

    public List<DocumentSummary> byShipment(UUID shipmentId) {
        shipments.detail(shipmentId);   // 404 si no es parte (o si es conductor de otro envío)
        return documents.listByShipment(shipmentId).stream().map(DocumentSummary::new).toList();
    }

    /** RF48: documentos de todos los envíos de la organización. */
    public Paged<DocumentSummary> mine(DocumentType type, PageRequest page) {
        return documents.listByOrganization(tenant.orgId(), type, page).map(DocumentSummary::new);
    }

    public Download download(UUID documentId) {
        Document d = documents.findById(documentId).orElseThrow(() -> BusinessException.notFound("Documento"));
        shipments.detail(d.shipmentId());
        try {
            return new Download(d, storage.open(d.storageKey()));
        } catch (IOException e) {
            throw BusinessException.notFound("Archivo");
        }
    }

    /** Para shipment (completar la entrega) y reputation (RN06). */
    public boolean hasDeliveryPhoto(UUID shipmentId, UUID stopId) {
        return documents.existsForStop(shipmentId, stopId, DocumentType.DELIVERY_PHOTO);
    }

    /** 2.5: la parada de recojo tiene al menos una foto de la carga. */
    public boolean hasPickupPhoto(UUID shipmentId, UUID stopId) {
        return documents.existsForStop(shipmentId, stopId, DocumentType.PICKUP_PHOTO);
    }

    /** 2.6/2.7: el documento existe, es del envío y quien llama lo ve (RLS); si no, 404. */
    public Document ofShipment(UUID shipmentId, UUID documentId) {
        Document d = documents.findById(documentId).orElseThrow(() -> BusinessException.notFound("Documento"));
        if (!d.shipmentId().equals(shipmentId)) throw BusinessException.notFound("Documento");
        return d;
    }

    /** RN06: el envío tiene al menos una foto de evidencia de entrega. */
    public boolean hasDeliveryPhoto(UUID shipmentId) {
        return documents.existsForShipment(shipmentId, DocumentType.DELIVERY_PHOTO);
    }
}
