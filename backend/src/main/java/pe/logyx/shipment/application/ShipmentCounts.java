package pe.logyx.shipment.application;

/** Contadores de envíos de la organización actual para los KPIs del dashboard. */
public record ShipmentCounts(long inTransit, long active, long deliveredThisMonth) {}
