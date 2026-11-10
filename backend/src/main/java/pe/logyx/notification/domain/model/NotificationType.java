package pe.logyx.notification.domain.model;

/** Evento que originó la notificación. */
public enum NotificationType {
    NEW_BID, BID_ACCEPTED, BID_REJECTED, COUNTER_OFFER, NEW_MESSAGE, SHIPMENT_STATUS_CHANGE,
    INCIDENT_REPORTED, INCIDENT_RESOLVED, NEW_REVIEW
}
