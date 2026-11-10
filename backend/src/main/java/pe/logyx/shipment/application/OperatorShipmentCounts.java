package pe.logyx.shipment.application;

/** Envíos de toda la plataforma por estado (métricas del operador). */
public record OperatorShipmentCounts(long pending, long inTransit, long delivered, long cancelled) {}
