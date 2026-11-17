package pe.logyx.routing.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.model.Detour;
import pe.logyx.routing.domain.model.PickupDelivery;
import pe.logyx.routing.domain.model.PickupDeliveryPlan;
import pe.logyx.routing.domain.model.PlannedStop;
import pe.logyx.routing.domain.model.RoadLeg;
import pe.logyx.routing.domain.model.RoutePlan;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.logyx.catalog.domain.City.*;

/** Red de juguete (km simétricos, 1 km = 1 min) para probar la lógica sin depender del dataset. */
class RouteCalculatorTest {

    private static final Map<City, Map<City, Double>> KM = new EnumMap<>(City.class);

    static {
        put(AREQUIPA, JULIACA, 250);
        put(AREQUIPA, PUNO, 275);
        put(JULIACA, PUNO, 45);
        put(AREQUIPA, CUSCO, 485);
        put(JULIACA, CUSCO, 345);
        put(PUNO, CUSCO, 385);
    }

    private static void put(City a, City b, double km) {
        KM.computeIfAbsent(a, k -> new EnumMap<>(City.class)).put(b, km);
        KM.computeIfAbsent(b, k -> new EnumMap<>(City.class)).put(a, km);
    }

    private final RouteCalculator calc = new RouteCalculator((from, to) ->
            Optional.ofNullable(KM.getOrDefault(from, Map.of()).get(to)).map(km -> new RoadLeg(from, to, km, km, false)));

    @Test
    void tramoConocidoYRespaldoEstimado() {
        assertEquals(45, calc.distanceKm(JULIACA, PUNO), 1e-9);
        assertEquals(0, calc.distanceKm(PUNO, PUNO), 1e-9);
        RoadLeg est = calc.leg(LIMA, ICA);
        assertTrue(est.estimated());
        assertEquals(StraightLineEstimate.haversineKm(LIMA.lat, LIMA.lng, ICA.lat, ICA.lng) * 1.35, est.distanceKm(), 1e-6);
    }

    @Test
    void recorridoSumaLosTramos() {
        RoutePlan r = calc.route(List.of(AREQUIPA, JULIACA, PUNO));
        assertEquals(295, r.distanceKm(), 1e-9);
        assertEquals(2, r.legs().size());
        assertFalse(r.estimated());
        assertEquals(0, calc.route(List.of(CUSCO)).distanceKm(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> calc.route(List.of()));
    }

    @Test
    void ordenOptimoDeParadas() {
        // Desde Arequipa, visitar Puno, Cusco y Juliaca: lo corto es Arequipa → Puno → Juliaca → Cusco (275 + 45 + 345).
        RoutePlan r = calc.optimize(AREQUIPA, List.of(PUNO, CUSCO, JULIACA), null, RouteCalculator.Objective.DISTANCE);
        assertEquals(List.of(AREQUIPA, PUNO, JULIACA, CUSCO), r.stops());
        assertEquals(665, r.distanceKm(), 1e-9);
        // Con llegada fija en Arequipa (vuelta).
        RoutePlan loop = calc.optimize(AREQUIPA, List.of(PUNO, CUSCO, JULIACA), AREQUIPA, RouteCalculator.Objective.DISTANCE);
        assertEquals(AREQUIPA, loop.stops().get(loop.stops().size() - 1));
        assertEquals(275 + 45 + 345 + 485, loop.distanceKm(), 1e-9); // Arequipa → Puno → Juliaca → Cusco → Arequipa
    }

    @Test
    void recojosAntesDeSusEntregas() {
        // Carga 0: Puno → Arequipa; carga 1: Juliaca → Cusco. Vehículo en Juliaca.
        PickupDeliveryPlan plan = calc.planPickupsAndDeliveries(JULIACA,
                List.of(new PickupDelivery(PUNO, AREQUIPA), new PickupDelivery(JULIACA, CUSCO)), null,
                RouteCalculator.Objective.DISTANCE);
        assertTrue(plan.exact());
        List<PlannedStop> s = plan.stops();
        assertEquals(PlannedStop.Kind.START, s.get(0).kind());
        for (int load = 0; load < 2; load++) {
            int finalLoad = load;
            int pick = indexOf(s, finalLoad, PlannedStop.Kind.PICKUP);
            int drop = indexOf(s, finalLoad, PlannedStop.Kind.DELIVERY);
            assertTrue(pick < drop, "recojo después de la entrega en la carga " + load);
        }
        // Óptimo: Juliaca(inicio) → Juliaca(recojo 1) → Puno → Arequipa → Cusco = 0 + 45 + 275 + 485 = 805
        // (frente a Juliaca → Puno → Cusco → Arequipa = 45 + 385 + 485 = 915).
        assertEquals(805, plan.route().distanceKm(), 1e-9);
    }

    @Test
    void desvioPorSumarUnaCargaAUnViaje() {
        // Viaje Arequipa → Cusco (485). Carga Juliaca → Puno: Arequipa → Juliaca → Puno → Cusco = 250 + 45 + 385 = 680.
        Detour d = calc.detour(List.of(AREQUIPA, CUSCO), new PickupDelivery(JULIACA, PUNO));
        assertEquals(485, d.base().distanceKm(), 1e-9);
        assertEquals(680, d.withLoad().distanceKm(), 1e-9);
        assertEquals(195, d.extraKm(), 1e-9);
        assertEquals(List.of(AREQUIPA, JULIACA, PUNO, CUSCO), d.withLoad().stops());
        assertEquals(1, d.pickupIndex());
        assertEquals(2, d.deliveryIndex());
        // Pasar por Juliaca de Arequipa a Puno: 250 + 45 − 275 = 20 km.
        assertEquals(20, calc.detourKm(AREQUIPA, JULIACA, PUNO), 1e-9);
        // Una carga en el mismo trayecto no desvía.
        assertEquals(0, calc.detour(List.of(AREQUIPA, CUSCO), new PickupDelivery(AREQUIPA, CUSCO)).extraKm(), 1e-9);
    }

    private static int indexOf(List<PlannedStop> s, int load, PlannedStop.Kind kind) {
        for (int i = 0; i < s.size(); i++) if (s.get(i).load() == load && s.get(i).kind() == kind) return i;
        throw new AssertionError("no está la parada");
    }
}
