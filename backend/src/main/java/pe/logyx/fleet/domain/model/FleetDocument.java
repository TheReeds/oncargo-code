package pe.logyx.fleet.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Documento de un vehículo o de un conductor, con vencimiento y archivo (V22). Es inmutable salvo la marca de
 * reemplazo: subir uno nuevo del mismo tipo deja el anterior en el historial ({@code supersededAt}).
 */
public final class FleetDocument {

    private final UUID id;
    private final UUID organizationId;
    private final FleetDocumentType type;
    private final UUID vehicleId;
    private final UUID driverId;
    private final String number;
    private final LocalDate expiresAt;
    private final String fileName;
    private final String contentType;
    private final long sizeBytes;
    private final String sha256;
    private final String storageKey;
    private final UUID uploadedBy;
    private final OffsetDateTime createdAt;
    private OffsetDateTime supersededAt;

    public FleetDocument(UUID id, UUID organizationId, FleetDocumentType type, UUID vehicleId, UUID driverId,
                         String number, LocalDate expiresAt, String fileName, String contentType, long sizeBytes,
                         String sha256, String storageKey, UUID uploadedBy, OffsetDateTime createdAt,
                         OffsetDateTime supersededAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.type = type;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.number = number;
        this.expiresAt = expiresAt;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
        this.supersededAt = supersededAt;
    }

    public boolean isCurrent() { return supersededAt == null; }

    public UUID ownerId() { return type.owner() == FleetDocumentType.Owner.VEHICLE ? vehicleId : driverId; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public FleetDocumentType type() { return type; }
    public UUID vehicleId() { return vehicleId; }
    public UUID driverId() { return driverId; }
    public String number() { return number; }
    public LocalDate expiresAt() { return expiresAt; }
    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public String storageKey() { return storageKey; }
    public UUID uploadedBy() { return uploadedBy; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime supersededAt() { return supersededAt; }
}
