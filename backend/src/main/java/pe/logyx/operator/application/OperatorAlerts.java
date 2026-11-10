package pe.logyx.operator.application;

import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.shipment.application.ShipmentSummary;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alertas SLA del operador (RF56/RF57/RF60): solicitudes publicadas sin ofertas tras el SLA (asignables, RN10),
 * subastas por vencer y envíos retenidos por una incidencia activa (RN11).
 */
public record OperatorAlerts(Duration sla, Duration expiringWindow, List<RequestSummary> withoutBids,
                             List<RequestSummary> expiringAuctions, List<IncidentSummary> activeIncidents,
                             Map<UUID, ShipmentSummary> incidentShipments) {}
