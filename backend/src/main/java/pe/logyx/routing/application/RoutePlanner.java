package pe.logyx.routing.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.application.port.out.RouteDataset;
import pe.logyx.routing.domain.RouteCalculator;
import pe.logyx.routing.domain.model.Detour;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.routing.domain.model.PickupDelivery;
import pe.logyx.routing.domain.model.PickupDeliveryPlan;
import pe.logyx.routing.domain.model.RoadLeg;
import pe.logyx.routing.domain.model.RoutePlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Punto de entrada del módulo {@code routing} para los demás módulos (ADR-0008): distancias por carretera para
 * cotizar (pricing), proximidad y desvíos (trip: retornos y Smart Load) y geometría para los mapas.
 * Sin estado ni BD: no necesita transacción.
 */
@ApplicationScoped
public class RoutePlanner {

    private final RouteDataset dataset;
    private final RouteCalculator calculator;

    @Inject
    public RoutePlanner(RouteDataset dataset) {
        this.dataset = dataset;
        this.calculator = new RouteCalculator(dataset.network());
    }

    /** Tramo por carretera (ruta más rápida del dataset). */
    public RoadLeg leg(City from, City to) {
        return calculator.leg(from, to);
    }

    /** Distancia por carretera en km (la que usa el motor de costos). */
    public double distanceKm(City from, City to) {
        return calculator.distanceKm(from, to);
    }

    /** Recorrido en el orden dado. */
    public RoutePlan route(List<City> stops) {
        return calculator.route(stops);
    }

    /** Orden más corto para visitar {@code stops} desde {@code start} (y hasta {@code end}, si no es null). */
    public RoutePlan shortestTour(City start, List<City> stops, City end) {
        return calculator.optimize(start, stops, end, RouteCalculator.Objective.DISTANCE);
    }

    /** Orden más corto de recojos y entregas (cada entrega después de su recojo). */
    public PickupDeliveryPlan planLoads(City start, List<PickupDelivery> loads, City end) {
        return calculator.planPickupsAndDeliveries(start, loads, end, RouteCalculator.Objective.DISTANCE);
    }

    /** Km extra de sumar una carga a un recorrido existente (mejor inserción). */
    public Detour detour(List<City> route, PickupDelivery load) {
        return calculator.detour(route, load);
    }

    /** Km extra de pasar por {@code via} yendo de {@code from} a {@code to}. */
    public double detourKm(City from, City via, City to) {
        return calculator.detourKm(from, via, to);
    }

    /**
     * Geometría del recorrido (concatenación de tramos). Si un tramo no tiene geometría, se dibuja recto entre las
     * ciudades (mismo comportamiento que antes de ADR-0008).
     */
    public List<LatLng> geometry(List<City> stops) {
        List<LatLng> out = new ArrayList<>();
        for (int i = 1; i < stops.size(); i++) {
            City a = stops.get(i - 1);
            City b = stops.get(i);
            if (a == b) continue;
            List<LatLng> part = dataset.geometry(a, b)
                    .orElseGet(() -> List.of(new LatLng(a.lat, a.lng), new LatLng(b.lat, b.lng)));
            out.addAll(out.isEmpty() ? part : part.subList(1, part.size()));
        }
        if (out.isEmpty() && !stops.isEmpty()) out.add(new LatLng(stops.get(0).lat, stops.get(0).lng));
        return out;
    }

    public String attribution() {
        return dataset.attribution();
    }
}
