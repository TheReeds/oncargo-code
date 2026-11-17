package pe.logyx.routing.domain.model;

import java.util.List;

/** Orden óptimo de recojos y entregas ({@code stops}) y el recorrido resultante ({@code route}). */
public record PickupDeliveryPlan(List<PlannedStop> stops, RoutePlan route, boolean exact) {

    public PickupDeliveryPlan {
        stops = List.copyOf(stops);
    }
}
