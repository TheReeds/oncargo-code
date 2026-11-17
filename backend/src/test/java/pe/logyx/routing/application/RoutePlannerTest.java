package pe.logyx.routing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.model.Detour;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.routing.domain.model.PickupDelivery;
import pe.logyx.routing.domain.model.PickupDeliveryPlan;
import pe.logyx.routing.domain.model.PlannedStop;
import pe.logyx.routing.domain.model.RoutePlan;
import pe.logyx.routing.infrastructure.dataset.ClasspathRouteDataset;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.logyx.catalog.domain.City.*;

/** Casos del corredor sobre el dataset real (sin Quarkus: se arma el bean a mano). */
class RoutePlannerTest {

    private static final RoutePlanner PLANNER =
            new RoutePlanner(new ClasspathRouteDataset(ClasspathRouteDataset.DEFAULT_RESOURCE, new ObjectMapper()));

    @Test
    void distanciaPorCarreteraNoEsLaLineaRecta() {
        // Antes: Cusco–Abancay = línea recta 99 km × 1.35 = 134 km; por carretera son ~208 km.
        assertTrue(PLANNER.distanceKm(CUSCO, ABANCAY) > 190);
        assertEquals(PLANNER.leg(CUSCO, ABANCAY).distanceKm(), PLANNER.distanceKm(CUSCO, ABANCAY), 1e-9);
    }

    @Test
    void smartLoadDesdeLimaEntregaPrimeroEnJuliaca() {
        // Datos demo: Lima → Juliaca y Lima → Puno en un mismo camión. Lo corto: Juliaca antes que Puno.
        PickupDeliveryPlan plan = PLANNER.planLoads(null,
                List.of(new PickupDelivery(LIMA, PUNO), new PickupDelivery(LIMA, JULIACA)), null);
        List<City> cities = plan.stops().stream().map(PlannedStop::city).toList();
        assertEquals(List.of(LIMA, LIMA, JULIACA, PUNO), cities);
        assertEquals(PLANNER.distanceKm(LIMA, JULIACA) + PLANNER.distanceKm(JULIACA, PUNO), plan.route().distanceKm(), 1e-6);
        assertFalse(plan.route().estimated());
    }

    @Test
    void desvioDeUnViajeLimaJuliacaPorUnaCargaArequipaPuno() {
        Detour d = PLANNER.detour(List.of(LIMA, JULIACA), new PickupDelivery(AREQUIPA, PUNO));
        // Partida y llegada fijas: la carga se recoge y se entrega antes de llegar a Juliaca.
        assertEquals(List.of(LIMA, AREQUIPA, PUNO, JULIACA), d.withLoad().stops());
        double expected = PLANNER.distanceKm(LIMA, AREQUIPA) + PLANNER.distanceKm(AREQUIPA, PUNO)
                + PLANNER.distanceKm(PUNO, JULIACA) - PLANNER.distanceKm(LIMA, JULIACA);
        assertEquals(expected, d.extraKm(), 1e-6);
        assertTrue(d.extraKm() < 120, "Arequipa queda casi de paso: " + d.extraKm());
    }

    @Test
    void recorridoMasCortoConVariasParadas() {
        RoutePlan r = PLANNER.shortestTour(AREQUIPA, List.of(TACNA, PUNO, MOQUEGUA, JULIACA), null);
        assertEquals(5, r.stops().size());
        assertEquals(AREQUIPA, r.stops().get(0));
        // El óptimo no debe ser peor que el orden "natural" que alguien escribiría a mano.
        double manual = PLANNER.route(List.of(AREQUIPA, JULIACA, PUNO, MOQUEGUA, TACNA)).distanceKm();
        assertTrue(r.distanceKm() <= manual + 1e-6);
    }

    @Test
    void geometriaConcatenaTramosSinRepetirPuntos() {
        List<LatLng> lj = PLANNER.geometry(List.of(LIMA, JULIACA));
        List<LatLng> jp = PLANNER.geometry(List.of(JULIACA, PUNO));
        List<LatLng> all = PLANNER.geometry(List.of(LIMA, JULIACA, PUNO));
        assertEquals(lj.size() + jp.size() - 1, all.size());
        assertEquals(lj.get(0), all.get(0));
        assertEquals(jp.get(jp.size() - 1), all.get(all.size() - 1));
        assertEquals(1, PLANNER.geometry(List.of(PUNO)).size());
        assertTrue(PLANNER.attribution().contains("OpenStreetMap"));
    }
}
