package pe.logyx.activity.domain.model;

/**
 * Sobre qué recurso se pide la actividad (autoría, roadmap v2 1.8): cada uno arrastra los recursos hijos cuyo rastro
 * interesa en su detalle (la solicitud, sus ofertas y contraofertas; el envío, sus incidencias, documentos y reseñas…).
 */
public enum ActivitySubject { REQUEST, BID, SHIPMENT, TRIP, INCIDENT }
