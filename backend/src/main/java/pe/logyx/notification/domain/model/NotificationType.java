package pe.logyx.notification.domain.model;

/** Evento que originó la notificación. */
public enum NotificationType {
    NEW_BID, BID_ACCEPTED, BID_REJECTED, COUNTER_OFFER, NEW_MESSAGE, SHIPMENT_STATUS_CHANGE,
    INCIDENT_REPORTED, INCIDENT_RESOLVED, NEW_REVIEW,
    TRIP_BOOKING_REQUESTED, TRIP_BOOKING_CONFIRMED, TRIP_BOOKING_REJECTED, TRIP_BOOKING_CANCELLED, TRIP_CANCELLED,
    SMART_LOAD_SUGGESTED, SMART_LOAD_ACCEPTED,
    /** V13: subasta cerrada por tiempo (F07), solicitud cancelada (F16), envío cancelado (F06), oferta retirada (F08),
     *  decisión del operador sobre la verificación de la organización (RN01). */
    AUCTION_CLOSED, REQUEST_CANCELLED, SHIPMENT_CANCELLED, BID_WITHDRAWN, ORGANIZATION_VERIFICATION,
    /** V16: un Lector pidió acceso; le llega a cada ADMIN de su organización. */
    ACCESS_REQUESTED,
    /** V22 (Ola 2, zona T): carga nueva que coincide con una búsqueda guardada (2.3); documento de flota por vencer o
     *  vencido (2.4); el transportista mejoró su oferta (2.8, a la PYME). */
    NEW_LOAD_MATCH, FLEET_DOCUMENT_EXPIRING, BID_IMPROVED,
    /** V21 (Ola 2, zona P): una plantilla programada publicó, dejó un borrador o falló (2.1); una PYME invita a ofertar a un
     *  preferido (2.9); segunda oferta: la PYME pide confirmar (al transportista) y él responde (a la PYME) (2.9). */
    TEMPLATE_RUN, REQUEST_INVITATION, BACKUP_REQUESTED, BACKUP_RESPONDED,
    /** V23 (Ola 2, zona X): ajuste del envío propuesto (a la PYME), decidido o retirado (a la contraparte) y escalado al
     *  operador por falta de respuesta (2.6); incidencia asignada a una persona del operador (2.7). */
    ADJUSTMENT_PROPOSED, ADJUSTMENT_DECIDED, ADJUSTMENT_ESCALATED, INCIDENT_ASSIGNED,
    /** V24 (Ola 2, zona F, 2.12): recordatorio de pago a la PYME, cambio de estado del cobro de un envío (a la
     *  contraparte) y cobro de la comisión de OnCargo registrado por el operador (solo al transportista). */
    PAYMENT_DUE, SETTLEMENT_UPDATED, COMMISSION_COLLECTED
}
