package pe.logyx.routing.infrastructure.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.StraightLineEstimate;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.routing.domain.model.RoadLeg;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Valida el dataset versionado {@code routing/routes.json} (ADR-0008) sin levantar Quarkus. */
class ClasspathRouteDatasetTest {

    private static final ClasspathRouteDataset DATASET =
            new ClasspathRouteDataset(ClasspathRouteDataset.DEFAULT_RESOURCE, new ObjectMapper());

    @Test
    void cubreTodosLosParesDelCatalogo() {
        for (City a : City.values()) {
            for (City b : City.values()) {
                if (a == b) continue;
                RoadLeg leg = DATASET.network().leg(a, b).orElseThrow(() -> new AssertionError("falta " + a + " → " + b));
                double straight = StraightLineEstimate.haversineKm(a.lat, a.lng, b.lat, b.lng);
                assertFalse(leg.estimated());
                assertTrue(leg.distanceKm() >= straight * 0.98, a + " → " + b + " más corto que la línea recta");
                assertTrue(leg.distanceKm() <= straight * 3.5, a + " → " + b + " sospechosamente largo");
                assertTrue(leg.durationMin() > 0);
                assertTrue(DATASET.geometry(a, b).isPresent(), "sin geometría " + a + " → " + b);
            }
        }
    }

    @Test
    void lasCoordenadasCoincidenConElEnumCity() {
        // Si alguien mueve o agrega una ciudad, hay que regenerar el dataset: python3 -I scripts/routing/build_routes.py
        assertEquals(City.values().length, DATASET.datasetCoordinates().size(), "dataset desactualizado: ciudades");
        for (City c : City.values()) {
            LatLng p = DATASET.datasetCoordinates().get(c);
            assertEquals(c.lat, p.lat(), 1e-6, "dataset desactualizado: " + c);
            assertEquals(c.lng, p.lng(), 1e-6, "dataset desactualizado: " + c);
        }
    }

    @Test
    void geometriaVaDeOrigenADestinoEnAmbosSentidos() {
        for (City a : City.values()) {
            for (City b : City.values()) {
                if (a == b) continue;
                List<LatLng> g = DATASET.geometry(a, b).orElseThrow();
                assertTrue(g.size() >= 2);
                assertTrue(km(g.get(0), a) < 5, "la línea " + a + " → " + b + " no empieza en " + a);
                assertTrue(km(g.get(g.size() - 1), b) < 5, "la línea " + a + " → " + b + " no termina en " + b);
            }
        }
    }

    @Test
    void valoresDeReferenciaDelCorredor() {
        assertEquals(43, DATASET.network().leg(City.JULIACA, City.PUNO).orElseThrow().distanceKm(), 3);
        assertEquals(1005, DATASET.network().leg(City.LIMA, City.AREQUIPA).orElseThrow().distanceKm(), 25);
        assertEquals(275, DATASET.network().leg(City.AREQUIPA, City.PUNO).orElseThrow().distanceKm(), 20);
        assertTrue(DATASET.attribution().contains("OpenStreetMap"));
    }

    private static double km(LatLng p, City c) {
        return StraightLineEstimate.haversineKm(p.lat(), p.lng(), c.lat, c.lng);
    }
}
