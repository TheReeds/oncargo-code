package pe.logyx.documents.infrastructure.persistence;

import pe.logyx.documents.domain.model.DocumentType;
import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.documents.application.port.out.DocumentRepository;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheDocumentRepository implements DocumentRepository {

    @Override
    public Optional<Document> findById(UUID id) {
        return DocumentEntity.<DocumentEntity>findByIdOptional(id).map(DocumentEntity::toDomain);
    }

    @Override
    public List<Document> listByShipment(UUID shipmentId) {
        return DocumentEntity.<DocumentEntity>list("shipmentId = ?1 order by createdAt, id", shipmentId).stream()
                .map(DocumentEntity::toDomain).toList();
    }

    @Override
    public Paged<Document> listByOrganization(UUID orgId, DocumentType type, PageRequest page) {
        String where = "(requesterOrgId = ?1 or carrierOrgId = ?1)";
        List<Object> params = new ArrayList<>(List.of(orgId));
        if (type != null) {
            where += " and type = ?2";
            params.add(type);
        }
        var q = DocumentEntity.<DocumentEntity>find(where + " order by createdAt desc, id", params.toArray())
                .page(page.page(), page.size());
        long total = DocumentEntity.count(where, params.toArray());
        return new Paged<>(q.list().stream().map(DocumentEntity::toDomain).toList(), page, total);
    }

    @Override
    public boolean existsForStop(UUID shipmentId, UUID stopId, DocumentType type) {
        return DocumentEntity.count("shipmentId = ?1 and stopId = ?2 and type = ?3", shipmentId, stopId, type) > 0;
    }

    @Override
    public boolean existsForShipment(UUID shipmentId, DocumentType type) {
        return DocumentEntity.count("shipmentId = ?1 and type = ?2", shipmentId, type) > 0;
    }

    @Override
    public void insert(Document document) {
        DocumentEntity.from(document).persist();
    }
}
