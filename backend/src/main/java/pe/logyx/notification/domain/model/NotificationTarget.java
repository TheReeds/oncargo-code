package pe.logyx.notification.domain.model;

/** Recurso al que lleva la notificación (el cliente arma la navegación con {@code relatedId}). */
public enum NotificationTarget { REQUEST, BID, COUNTER_OFFER, SHIPMENT, INCIDENT, REVIEW, TRIP, SMART_LOAD }
