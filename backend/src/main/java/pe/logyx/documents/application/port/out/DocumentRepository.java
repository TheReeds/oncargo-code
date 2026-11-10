package pe.logyx.documents.application.port.out;

import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Optional<Document> findById(UUID id);
    /** Más antiguos primero. */
    List<Document> listByShipment(UUID shipmentId);
    /** Documentos de los envíos de la organización (como PYME o transportista), más recientes primero. */
    Paged<Document> listByOrganization(UUID orgId, DocumentType type, PageRequest page);
    boolean existsForStop(UUID shipmentId, UUID stopId, DocumentType type);
    boolean existsForShipment(UUID shipmentId, DocumentType type);
    void insert(Document document);
}
