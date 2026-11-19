package pe.logyx.documents.domain.model;

/** Tipo de documento de un envío (enum con nombre propio: es un schema estable del contrato OpenAPI). */
public enum DocumentType {
    SERVICE_ORDER, REMISSION_GUIDE, RECEIPT, DELIVERY_PHOTO, SIGNATURE, OTHER,
    /** V23 (roadmap 2.5): foto de la carga en el recojo; solo el conductor asignado, ligada a la parada de recojo. */
    PICKUP_PHOTO;

    /** Evidencia de entrega: solo imágenes, solo el lado transportista y ligada a la parada de entrega. */
    public boolean isDeliveryEvidence() { return this == DELIVERY_PHOTO || this == SIGNATURE; }

    /** Evidencia del recojo (2.5): las mismas reglas que la de entrega, pero en la parada de recojo. */
    public boolean isPickupEvidence() { return this == PICKUP_PHOTO; }

    /** Evidencia de una parada: imagen del conductor asignado entre la llegada y el cierre de esa parada. */
    public boolean isStopEvidence() { return isDeliveryEvidence() || isPickupEvidence(); }
}
