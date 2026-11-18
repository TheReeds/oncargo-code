package pe.logyx.shipment.application;

import java.math.BigDecimal;

/**
 * Envíos de toda la plataforma por estado y valor transaccionado (métricas del operador). {@code gmv}: suma del precio
 * acordado de los envíos no cancelados; {@code gmvDelivered}: solo los entregados.
 */
public record OperatorShipmentCounts(long pending, long inTransit, long delivered, long cancelled,
                                     BigDecimal gmv, BigDecimal gmvDelivered) {}
