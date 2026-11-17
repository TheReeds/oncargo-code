package pe.logyx.incident.domain.model;

/**
 * Estado final del envío que fija el operador al resolver o cerrar una incidencia activa (RF55): el envío
 * continúa ("reencauzado", {@code RESUME}), se da por entregado ({@code DELIVERED}) o se cancela ({@code CANCELLED}).
 * Enum con nombre propio: es un schema estable del contrato OpenAPI.
 */
public enum IncidentShipmentOutcome { RESUME, DELIVERED, CANCELLED }
