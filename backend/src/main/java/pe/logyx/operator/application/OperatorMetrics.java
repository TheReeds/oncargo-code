package pe.logyx.operator.application;

import java.math.BigDecimal;

/** Métricas globales simples del panel del operador (RF56). {@code gmv}: precio acordado de los envíos no cancelados. */
public record OperatorMetrics(long openRequests, long requestsAtRisk, long matchedRequests, long cancelledRequests,
                              long shipmentsPending, long shipmentsInTransit, long shipmentsDelivered,
                              long activeIncidents, long pymes, long carriers,
                              BigDecimal gmv, BigDecimal gmvDelivered) {}
