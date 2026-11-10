package pe.logyx.documents.domain.model;

/** Tipo de documento de un envío (enum con nombre propio: es un schema estable del contrato OpenAPI). */
public enum DocumentType {
    SERVICE_ORDER, REMISSION_GUIDE, RECEIPT, DELIVERY_PHOTO, SIGNATURE, OTHER;

    /** Evidencia de entrega: solo imágenes, solo el lado transportista y ligada a la parada de entrega. */
    public boolean isDeliveryEvidence() { return this == DELIVERY_PHOTO || this == SIGNATURE; }
}
