package pe.logyx.documents.domain.model;

/** Tipo de documento de un envío (enum con nombre propio: es un schema estable del contrato OpenAPI). */
public enum DocumentType {
    SERVICE_ORDER, REMISSION_GUIDE, RECEIPT, DELIVERY_PHOTO, SIGNATURE, OTHER,
    /** V23 (roadmap 2.5): foto de la carga en el recojo; solo el conductor asignado, ligada a la parada de recojo. */
    PICKUP_PHOTO,
    /** V24 (roadmap 2.12): constancia del pago de la PYME (voucher de transferencia, captura de Yape/Plin). Solo la PYME. */
    PAYMENT_PROOF,
    /** V24 (roadmap 2.12): factura o boleta que el transportista emitió por el flete (PDF o imagen). Solo su oficina. */
    CARRIER_INVOICE;

    /** Documento del cobro (2.12): lo sube una sola de las partes, nunca el conductor. */
    public boolean isSettlementDocument() { return this == PAYMENT_PROOF || this == CARRIER_INVOICE; }

    /** Evidencia de entrega: solo imágenes, solo el lado transportista y ligada a la parada de entrega. */
    public boolean isDeliveryEvidence() { return this == DELIVERY_PHOTO || this == SIGNATURE; }

    /** Evidencia del recojo (2.5): las mismas reglas que la de entrega, pero en la parada de recojo. */
    public boolean isPickupEvidence() { return this == PICKUP_PHOTO; }

    /** Evidencia de una parada: imagen del conductor asignado entre la llegada y el cierre de esa parada. */
    public boolean isStopEvidence() { return isDeliveryEvidence() || isPickupEvidence(); }
}
