package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Smart Load Planner básico (OE07, RF30). Combina 2 o más solicitudes publicadas en un mismo vehículo disponible
 * cuando son compatibles entre sí:
 * <ul>
 *   <li>orígenes a ≤ 50 km y destinos a ≤ 80 km ({@link CityProximityPolicy});</li>
 *   <li>fechas de recojo con ±1 día de diferencia;</li>
 *   <li>peso combinado ≤ capacidad del vehículo y ocupación ≥ {@link #MIN_OCCUPANCY} (60 %);</li>
 *   <li>el tipo de vehículo admite cada carga ({@link VehicleCargoRules}) y la carga peligrosa no se mezcla con otras.</li>
 * </ul>
 * Por vehículo propone a lo más una combinación (la de mayor ingreso, luego mayor ocupación, luego menos ítems). Los
 * vehículos se procesan de menor a mayor capacidad y una solicitud no se reparte entre dos sugerencias del mismo ciclo.
 * Las cargas se agrupan en conjuntos conectados por compatibilidad y en cada grupo se evalúan como mucho
 * {@link #MAX_CANDIDATES} solicitudes (las de recojo más próximo) y {@link #MAX_ITEMS} ítems por combinación. Ingreso estimado = suma de los precios sugeridos de las solicitudes.
 * Ruta: recojos por fecha y cercanía al primer origen; entregas por cercanía al último recojo.
 */
public final class SmartLoadPlanner {

    public static final BigDecimal MIN_OCCUPANCY = new BigDecimal("0.60");
    public static final int MAX_ITEMS = 4;
    public static final int MAX_CANDIDATES = 12;
    public static final int DATE_TOLERANCE_DAYS = 1;

    private SmartLoadPlanner() {}

    public record Load(UUID requestId, UUID requesterOrgId, City origin, City destination, BigDecimal weightKg,
                       LocalDate pickupDate, BigDecimal income, CargoType cargoType) {}

    public record VehicleSlot(UUID vehicleId, Vehicle.Type type, String plate, BigDecimal capacityKg) {}

    public enum StopType { PICKUP, DELIVERY }

    public record Stop(StopType type, City city, UUID requestId) {}

    /** Cargas en orden de recojo; {@code loads.get(i)} tiene pickupOrder = i. */
    public record Plan(VehicleSlot vehicle, List<Load> loads, List<Integer> deliveryOrder, BigDecimal totalWeightKg,
                       BigDecimal income, BigDecimal occupancyPct, List<Stop> route) {}

    public static List<Plan> plan(List<VehicleSlot> vehicles, List<Load> loads) {
        List<VehicleSlot> ordered = vehicles.stream()
                .sorted(Comparator.comparing(VehicleSlot::capacityKg).thenComparing(VehicleSlot::vehicleId)).toList();
        Set<UUID> used = new HashSet<>();
        List<Plan> out = new ArrayList<>();
        for (VehicleSlot v : ordered) {
            List<Load> pool = loads.stream()
                    .filter(l -> !used.contains(l.requestId()))
                    .filter(l -> l.weightKg().compareTo(v.capacityKg()) <= 0)
                    .filter(l -> VehicleCargoRules.canCarry(v.type(), l.cargoType()))
                    .sorted(Comparator.comparing(Load::pickupDate).thenComparing(Load::requestId)).toList();
            Plan best = null;
            for (List<Load> cluster : clusters(pool)) {
                Plan p = bestFor(v, cluster.size() > MAX_CANDIDATES ? cluster.subList(0, MAX_CANDIDATES) : cluster);
                if (p != null && (best == null || better(p, best))) best = p;
            }
            if (best != null) {
                best.loads().forEach(l -> used.add(l.requestId()));
                out.add(best);
            }
        }
        return out;
    }

    /** Agrupa las cargas conectadas por compatibilidad: una combinación solo puede salir de un mismo grupo. */
    static List<List<Load>> clusters(List<Load> pool) {
        int n = pool.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (compatible(pool.get(i), pool.get(j))) parent[find(parent, i)] = find(parent, j);
            }
        }
        Map<Integer, List<Load>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(pool.get(i));
        return groups.values().stream().filter(g -> g.size() >= 2).toList();
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static boolean better(Plan a, Plan b) {
        int c = a.income().compareTo(b.income());
        if (c != 0) return c > 0;
        c = a.totalWeightKg().compareTo(b.totalWeightKg());
        return c > 0;
    }

    static boolean compatible(Load a, Load b) {
        if (ChronoUnit.DAYS.between(a.pickupDate(), b.pickupDate()) > DATE_TOLERANCE_DAYS
                || ChronoUnit.DAYS.between(b.pickupDate(), a.pickupDate()) > DATE_TOLERANCE_DAYS) return false;
        if (!CityProximityPolicy.within(a.origin(), b.origin(), CityProximityPolicy.SMART_ORIGIN_RADIUS_KM)) return false;
        if (!CityProximityPolicy.within(a.destination(), b.destination(),
                CityProximityPolicy.SMART_DESTINATION_RADIUS_KM)) return false;
        boolean hazard = a.cargoType() == CargoType.HAZARDOUS || b.cargoType() == CargoType.HAZARDOUS;
        return !hazard || a.cargoType() == b.cargoType();
    }

    private static Plan bestFor(VehicleSlot v, List<Load> pool) {
        Best best = new Best();
        search(v, pool, 0, new ArrayList<>(), BigDecimal.ZERO, best);
        return best.combo == null ? null : build(v, best.combo);
    }

    private static final class Best {
        List<Load> combo;
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;

        boolean beatenBy(List<Load> candidate, BigDecimal candIncome, BigDecimal candWeight) {
            if (combo == null) return true;
            int c = candIncome.compareTo(income);
            if (c != 0) return c > 0;
            c = candWeight.compareTo(weight);
            if (c != 0) return c > 0;
            return candidate.size() < combo.size();
        }
    }

    private static void search(VehicleSlot v, List<Load> pool, int from, List<Load> current, BigDecimal weight,
                               Best best) {
        if (current.size() >= 2 && meetsOccupancy(weight, v.capacityKg())) {
            BigDecimal income = current.stream().map(Load::income).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (best.beatenBy(current, income, weight)) {
                best.combo = List.copyOf(current);
                best.income = income;
                best.weight = weight;
            }
        }
        if (current.size() == MAX_ITEMS) return;
        for (int i = from; i < pool.size(); i++) {
            Load next = pool.get(i);
            BigDecimal w = weight.add(next.weightKg());
            if (w.compareTo(v.capacityKg()) > 0) continue;
            if (current.stream().anyMatch(l -> !compatible(l, next))) continue;
            current.add(next);
            search(v, pool, i + 1, current, w, best);
            current.remove(current.size() - 1);
        }
    }

    static boolean meetsOccupancy(BigDecimal weight, BigDecimal capacity) {
        return weight.compareTo(capacity.multiply(MIN_OCCUPANCY)) >= 0;
    }

    private static Plan build(VehicleSlot v, List<Load> combo) {
        City firstOrigin = combo.stream().min(Comparator.comparing(Load::pickupDate)).orElseThrow().origin();
        List<Load> pickups = combo.stream()
                .sorted(Comparator.comparing(Load::pickupDate)
                        .thenComparingDouble(l -> CityProximityPolicy.distanceKm(firstOrigin, l.origin()))
                        .thenComparing(Load::requestId)).toList();
        City lastPickup = pickups.get(pickups.size() - 1).origin();
        List<Load> deliveries = pickups.stream()
                .sorted(Comparator.comparingDouble((Load l) -> CityProximityPolicy.distanceKm(lastPickup, l.destination()))
                        .thenComparing(Load::requestId)).toList();
        List<Integer> deliveryOrder = pickups.stream().map(deliveries::indexOf).toList();

        List<Stop> route = new ArrayList<>();
        pickups.forEach(l -> route.add(new Stop(StopType.PICKUP, l.origin(), l.requestId())));
        deliveries.forEach(l -> route.add(new Stop(StopType.DELIVERY, l.destination(), l.requestId())));

        BigDecimal weight = pickups.stream().map(Load::weightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = pickups.stream().map(Load::income).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pct = weight.multiply(BigDecimal.valueOf(100)).divide(v.capacityKg(), 2, RoundingMode.HALF_UP);
        return new Plan(v, pickups, deliveryOrder, weight, income, pct, route);
    }
}
