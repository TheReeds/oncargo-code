package pe.logyx.documents.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Archivo asociado a un envío: guía de remisión, orden de servicio, comprobante o evidencia de entrega (foto y
 * firma). El contenido vive en el almacenamiento ({@code storageKey}); aquí solo los metadatos.
 */
public final class Document {

    private final UUID id;
    private final UUID shipmentId;
    private final UUID stopId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final DocumentType type;
    private final String fileName;
    private final String contentType;
    private final long sizeBytes;
    private final String sha256;
    private final String storageKey;
    private final UUID uploadedBy;
    private final UUID uploadedByOrgId;
    private final OffsetDateTime createdAt;

    public Document(UUID id, UUID shipmentId, UUID stopId, UUID requesterOrgId, UUID carrierOrgId, DocumentType type,
                    String fileName, String contentType, long sizeBytes, String sha256, String storageKey,
                    UUID uploadedBy, UUID uploadedByOrgId, OffsetDateTime createdAt) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.stopId = stopId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.type = type;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedByOrgId = uploadedByOrgId;
        this.createdAt = createdAt;
    }

    /** Clave de almacenamiento: generada por el servidor (nunca deriva del nombre que envía el cliente). */
    public static String storageKeyFor(UUID shipmentId, UUID documentId, String extension) {
        return "shipments/" + shipmentId + "/" + documentId + "." + extension;
    }

    public UUID id() { return id; }
    public UUID shipmentId() { return shipmentId; }
    public UUID stopId() { return stopId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public DocumentType type() { return type; }
    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public String storageKey() { return storageKey; }
    public UUID uploadedBy() { return uploadedBy; }
    public UUID uploadedByOrgId() { return uploadedByOrgId; }
    public OffsetDateTime createdAt() { return createdAt; }
}
