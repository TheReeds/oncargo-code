package pe.logyx.routing.domain.model;

import pe.logyx.catalog.domain.City;

/** Una carga a mover: se recoge en {@code pickup} y se entrega en {@code delivery} (después del recojo). */
public record PickupDelivery(City pickup, City delivery) {}
