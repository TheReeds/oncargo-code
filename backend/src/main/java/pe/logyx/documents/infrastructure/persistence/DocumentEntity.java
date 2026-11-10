package pe.logyx.documents.infrastructure.persistence;

import pe.logyx.documents.domain.model.DocumentType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.documents.domain.model.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "stop_id")
    public UUID stopId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Enumerated(EnumType.STRING)
    public DocumentType type;

    @Column(name = "file_name")
    public String fileName;

    @Column(name = "content_type")
    public String contentType;

    @Column(name = "file_size_bytes")
    public long sizeBytes;

    public String sha256;

    @Column(name = "storage_key")
    public String storageKey;

    @Column(name = "uploaded_by")
    public UUID uploadedBy;

    @Column(name = "uploaded_by_org_id")
    public UUID uploadedByOrgId;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Document toDomain() {
        return new Document(id, shipmentId, stopId, requesterOrgId, carrierOrgId, type, fileName, contentType,
                sizeBytes, sha256, storageKey, uploadedBy, uploadedByOrgId, createdAt);
    }

    static DocumentEntity from(Document d) {
        DocumentEntity e = new DocumentEntity();
        e.id = d.id();
        e.shipmentId = d.shipmentId();
        e.stopId = d.stopId();
        e.requesterOrgId = d.requesterOrgId();
        e.carrierOrgId = d.carrierOrgId();
        e.type = d.type();
        e.fileName = d.fileName();
        e.contentType = d.contentType();
        e.sizeBytes = d.sizeBytes();
        e.sha256 = d.sha256();
        e.storageKey = d.storageKey();
        e.uploadedBy = d.uploadedBy();
        e.uploadedByOrgId = d.uploadedByOrgId();
        e.createdAt = d.createdAt();
        return e;
    }
}
