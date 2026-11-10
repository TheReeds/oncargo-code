package pe.logyx.documents.infrastructure.web;

import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.documents.application.DocumentSummary;
import pe.logyx.documents.domain.model.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() {}

    /** Metadatos de un documento; el contenido se descarga en {@code downloadUrl} (con el mismo token). */
    public record DocumentView(UUID id, UUID shipmentId, UUID stopId, DocumentType type, String fileName,
                               String contentType, long sizeBytes, UUID uploadedByOrgId, OffsetDateTime createdAt,
                               String downloadUrl) {
        static DocumentView of(DocumentSummary s) {
            Document d = s.document();
            return new DocumentView(d.id(), d.shipmentId(), d.stopId(), d.type(), d.fileName(), d.contentType(),
                    d.sizeBytes(), d.uploadedByOrgId(), d.createdAt(), "/api/v1/documents/" + d.id() + "/file");
        }
    }
}
