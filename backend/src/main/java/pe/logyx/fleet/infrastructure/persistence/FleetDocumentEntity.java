package pe.logyx.fleet.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fleet_documents")
public class FleetDocumentEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    @Column(name = "owner_type")
    public String ownerType;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    @Column(name = "driver_id")
    public UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    public FleetDocumentType docType;

    public String number;

    @Column(name = "expires_at")
    public LocalDate expiresAt;

    @Column(name = "file_name")
    public String fileName;

    @Column(name = "content_type")
    public String contentType;

    @Column(name = "file_size_bytes")
    public long fileSizeBytes;

    public String sha256;

    @Column(name = "storage_key")
    public String storageKey;

    @Column(name = "uploaded_by")
    public UUID uploadedBy;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "superseded_at")
    public OffsetDateTime supersededAt;

    FleetDocument toDomain() {
        return new FleetDocument(id, organizationId, docType, vehicleId, driverId, number, expiresAt, fileName,
                contentType, fileSizeBytes, sha256, storageKey, uploadedBy, createdAt, supersededAt);
    }

    static FleetDocumentEntity of(FleetDocument d) {
        FleetDocumentEntity e = new FleetDocumentEntity();
        e.id = d.id();
        e.organizationId = d.organizationId();
        e.ownerType = d.type().owner().name();
        e.vehicleId = d.vehicleId();
        e.driverId = d.driverId();
        e.docType = d.type();
        e.number = d.number();
        e.expiresAt = d.expiresAt();
        e.fileName = d.fileName();
        e.contentType = d.contentType();
        e.fileSizeBytes = d.sizeBytes();
        e.sha256 = d.sha256();
        e.storageKey = d.storageKey();
        e.uploadedBy = d.uploadedBy();
        e.createdAt = d.createdAt();
        e.supersededAt = d.supersededAt();
        return e;
    }
}
