package pe.logyx.routing.domain;

import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.model.Detour;
import pe.logyx.routing.domain.model.PickupDelivery;
import pe.logyx.routing.domain.model.PickupDeliveryPlan;
import pe.logyx.routing.domain.model.PlannedStop;
import pe.logyx.routing.domain.model.RoadLeg;
import pe.logyx.routing.domain.model.RoutePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cálculos de ruta por carretera sobre una {@link RoadNetwork} (ADR-0008): tramo, recorrido en orden dado, orden
 * óptimo de paradas, orden óptimo de recojos/entregas (Smart Load, retornos) y desvío por sumar una carga a un viaje.
 * Si un par falta en la red se usa {@link StraightLineEstimate} y el tramo queda marcado como estimado.
 */
public final class RouteCalculator {

    /** Qué minimizar al ordenar paradas. */
    public enum Objective { DISTANCE, DURATION }

    private final RoadNetwork network;

    public RouteCalculator(RoadNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    public RoadLeg leg(City from, City to) {
        if (from == to) return RoadLeg.zero(from);
        return network.leg(from, to).orElseGet(() -> StraightLineEstimate.leg(from, to));
    }

    public double distanceKm(City from, City to) {
        return leg(from, to).distanceKm();
    }

    /** Recorrido en el orden dado (al menos una ciudad). */
    public RoutePlan route(List<City> stops) {
        if (stops.isEmpty()) throw new IllegalArgumentException("El recorrido necesita al menos una ciudad");
        List<RoadLeg> legs = new ArrayList<>(stops.size() - 1);
        double km = 0;
        double min = 0;
        for (int i = 1; i < stops.size(); i++) {
            RoadLeg l = leg(stops.get(i - 1), stops.get(i));
            legs.add(l);
            km += l.distanceKm();
            min += l.durationMin();
        }
        return new RoutePlan(stops, legs, km, min);
    }

    /** Km extra de pasar por {@code via} yendo de {@code from} a {@code to}. */
    public double detourKm(City from, City via, City to) {
        return Math.max(0, distanceKm(from, via) + distanceKm(via, to) - distanceKm(from, to));
    }

    /**
     * Orden de menor costo para visitar {@code stops} una vez cada una, saliendo de {@code start} y, si no es null,
     * terminando en {@code end}.
     */
    public RoutePlan optimize(City start, List<City> stops, City end, Objective objective) {
        List<City> nodes = new ArrayList<>();
        nodes.add(Objects.requireNonNull(start));
        nodes.addAll(stops);
        if (end != null) nodes.add(end);
        int[] order = StopOrderOptimizer.solve(matrix(nodes, objective), 0, end != null ? nodes.size() - 1 : -1, null).order();
        List<City> ordered = new ArrayList<>(order.length);
        for (int i : order) ordered.add(nodes.get(i));
        return route(ordered);
    }

    /**
     * Orden óptimo de recojos y entregas: cada entrega va después de su recojo. {@code start}/{@code end} son
     * opcionales (null = el recorrido empieza en algún recojo / termina en alguna entrega).
     */
    public PickupDeliveryPlan planPickupsAndDeliveries(City start, List<PickupDelivery> loads, City end, Objective objective) {
        if (loads.isEmpty()) throw new IllegalArgumentException("Se necesita al menos una carga");
        List<PlannedStop> nodes = new ArrayList<>();
        if (start != null) nodes.add(new PlannedStop(start, -1, PlannedStop.Kind.START));
        List<Integer> pred = new ArrayList<>();
        if (start != null) pred.add(-1);
        for (int i = 0; i < loads.size(); i++) {
            nodes.add(new PlannedStop(loads.get(i).pickup(), i, PlannedStop.Kind.PICKUP));
            pred.add(-1);
            nodes.add(new PlannedStop(loads.get(i).delivery(), i, PlannedStop.Kind.DELIVERY));
            pred.add(nodes.size() - 2);
        }
        if (end != null) {
            nodes.add(new PlannedStop(end, -1, PlannedStop.Kind.END));
            pred.add(-1);
        }
        List<City> cities = nodes.stream().map(PlannedStop::city).toList();
        StopOrderOptimizer.Result r = StopOrderOptimizer.solve(matrix(cities, objective), start != null ? 0 : -1,
                end != null ? nodes.size() - 1 : -1, pred.stream().mapToInt(Integer::intValue).toArray());
        List<PlannedStop> ordered = new ArrayList<>(r.order().length);
        for (int i : r.order()) ordered.add(nodes.get(i));
        return new PickupDeliveryPlan(ordered, route(ordered.stream().map(PlannedStop::city).toList()), r.exact());
    }

    /**
     * Mejor inserción de una carga en un recorrido existente (p. ej. un viaje publicado Lima → Juliaca que suma una
     * carga Arequipa → Puno): la partida y la llegada del recorrido no se mueven; el recojo va antes que la entrega.
     */
    public Detour detour(List<City> route, PickupDelivery load) {
        if (route.size() < 2) throw new IllegalArgumentException("El recorrido necesita partida y llegada");
        RoutePlan base = route(route);
        RoutePlan best = null;
        int bestP = -1;
        int bestQ = -1;
        for (int p = 1; p < route.size(); p++) {
            for (int q = p; q < route.size(); q++) {
                List<City> cand = new ArrayList<>(route.size() + 2);
                cand.addAll(route.subList(0, p));
                cand.add(load.pickup());
                cand.addAll(route.subList(p, q));
                cand.add(load.delivery());
                cand.addAll(route.subList(q, route.size()));
                RoutePlan plan = route(cand);
                if (best == null || plan.distanceKm() < best.distanceKm() - 1e-9) {
                    best = plan;
                    bestP = p;
                    bestQ = q + 1;
                }
            }
        }
        return new Detour(base, best, bestP, bestQ);
    }

    private double[][] matrix(List<City> nodes, Objective objective) {
        int n = nodes.size();
        double[][] c = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                RoadLeg l = leg(nodes.get(i), nodes.get(j));
                c[i][j] = objective == Objective.DURATION ? l.durationMin() : l.distanceKm();
            }
        }
        return c;
    }
}
