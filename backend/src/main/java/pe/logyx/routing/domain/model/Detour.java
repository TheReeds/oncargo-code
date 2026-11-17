package pe.logyx.routing.domain.model;

/**
 * Desvío por agregar una carga (recojo + entrega) a un recorrido existente: km del recorrido original, km con la carga,
 * km extra y posiciones (índices en el recorrido nuevo) donde quedan el recojo y la entrega.
 */
public record Detour(RoutePlan base, RoutePlan withLoad, int pickupIndex, int deliveryIndex) {

    public double extraKm() {
        return Math.max(0, withLoad.distanceKm() - base.distanceKm());
    }

    public double extraMin() {
        return Math.max(0, withLoad.durationMin() - base.durationMin());
    }
}
