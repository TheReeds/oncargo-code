package pe.logyx.operator.application;

import java.math.BigDecimal;

/**
 * Métricas globales simples del panel del operador (RF56). {@code gmv}: precio acordado de los envíos no cancelados.
 * Los últimos cuatro son los parámetros de sistema vigentes (V18) que la UI usa para colorear y explicar.
 */
public record OperatorMetrics(long openRequests, long requestsAtRisk, long matchedRequests, long cancelledRequests,
                              long shipmentsPending, long shipmentsInTransit, long shipmentsDelivered,
                              long activeIncidents, long pymes, long carriers,
                              BigDecimal gmv, BigDecimal gmvDelivered,
                              long assignmentSlaHours, long incidentWarnHours, long incidentBreachHours,
                              BigDecimal commissionRate) {}
