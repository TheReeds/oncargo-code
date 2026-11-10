package pe.logyx.operator.application;

/** Métricas globales simples del panel del operador (RF56). */
public record OperatorMetrics(long openRequests, long requestsAtRisk, long matchedRequests, long cancelledRequests,
                              long shipmentsPending, long shipmentsInTransit, long shipmentsDelivered,
                              long activeIncidents, long pymes, long carriers) {}
