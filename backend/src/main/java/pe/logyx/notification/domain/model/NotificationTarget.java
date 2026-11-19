package pe.logyx.notification.domain.model;

/**
 * Recurso al que lleva la notificación (el cliente arma la navegación con {@code relatedId}). V16: {@code USER} = una
 * persona de la propia organización (p. ej. quien pidió acceso; el ADMIN le cambia el rol).
 */
public enum NotificationTarget { REQUEST, BID, COUNTER_OFFER, SHIPMENT, INCIDENT, REVIEW, TRIP, SMART_LOAD, ORGANIZATION, USER }
