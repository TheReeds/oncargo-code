package pe.logyx.notification.domain.model;

/** Evento que originó la notificación. */
public enum NotificationType {
    NEW_BID, BID_ACCEPTED, BID_REJECTED, COUNTER_OFFER, NEW_MESSAGE, SHIPMENT_STATUS_CHANGE,
    INCIDENT_REPORTED, INCIDENT_RESOLVED, NEW_REVIEW,
    TRIP_BOOKING_REQUESTED, TRIP_BOOKING_CONFIRMED, TRIP_BOOKING_REJECTED, TRIP_BOOKING_CANCELLED, TRIP_CANCELLED,
    SMART_LOAD_SUGGESTED, SMART_LOAD_ACCEPTED,
    /** V13: subasta cerrada por tiempo (F07), solicitud cancelada (F16), envío cancelado (F06), oferta retirada (F08),
     *  decisión del operador sobre la verificación de la organización (RN01). */
    AUCTION_CLOSED, REQUEST_CANCELLED, SHIPMENT_CANCELLED, BID_WITHDRAWN, ORGANIZATION_VERIFICATION
}
