package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Rutas por carretera (ADR-0008): {@code GET /api/v1/routes} y la distancia que usa la cotización. */
@QuarkusTest
class RoutingFlowTest {

    @Test
    void rutaPorCarreteraConTramosYGeometria() {
        Session pyme = register("PYME", "Rutas Pyme");
        as(pyme).queryParam("stops", "LIMA,AREQUIPA,PUNO").get("/api/v1/routes").then().statusCode(200)
                .body("data.stops", contains("LIMA", "AREQUIPA", "PUNO"))
                .body("data.legs", hasSize(2))
                .body("data.legs[0].fromCity", is("LIMA")).body("data.legs[0].toCity", is("AREQUIPA"))
                .body("data.legs[0].distanceKm", num(1001.6)).body("data.legs[1].distanceKm", num(273.8))
                .body("data.distanceKm", num(1275.4)).body("data.durationMin", notNullValue())
                .body("data.estimated", is(false))
                .body("data.polyline", not(emptyOrNullString())).body("data.polylinePrecision", is(5))
                .body("data.attribution", containsString("OpenStreetMap"));
    }

    @Test
    void optimizarReordenaSoloLasParadasIntermedias() {
        Session carrier = register("CARRIER", "Rutas Carrier");
        float manual = as(carrier).queryParam("stops", "AREQUIPA,TACNA,JULIACA,MOQUEGUA,PUNO").get("/api/v1/routes")
                .then().statusCode(200).extract().path("data.distanceKm");
        float optimized = as(carrier).queryParam("stops", "AREQUIPA,TACNA,JULIACA,MOQUEGUA,PUNO").queryParam("optimize", true)
                .get("/api/v1/routes").then().statusCode(200)
                .body("data.stops[0]", is("AREQUIPA")).body("data.stops[4]", is("PUNO"))
                .body("data.stops", containsInAnyOrder("AREQUIPA", "TACNA", "JULIACA", "MOQUEGUA", "PUNO"))
                .extract().path("data.distanceKm");
        org.junit.jupiter.api.Assertions.assertTrue(optimized < manual, optimized + " >= " + manual);
    }

    @Test
    void validaParadasYExigeSesion() {
        Session pyme = register("PYME", "Rutas Errores");
        as(pyme).queryParam("stops", "LIMA").get("/api/v1/routes").then().statusCode(400)
                .body("error.code", is("LOGYX-RTE-001")).body("error.field", is("stops"));
        as(pyme).get("/api/v1/routes").then().statusCode(400).body("error.code", is("LOGYX-RTE-001"));
        as(pyme).queryParam("stops", "LIMA,BOGOTA").get("/api/v1/routes").then().statusCode(400)
                .body("error.code", is("LOGYX-PRC-001"));
        as(pyme).queryParam("stops", String.join(",", Collections.nCopies(26, "LIMA"))).get("/api/v1/routes")
                .then().statusCode(400).body("error.code", is("LOGYX-RTE-002"));
        RestAssured.given().queryParam("stops", "LIMA,PUNO").get("/api/v1/routes").then().statusCode(401);
    }

    @Test
    void laCotizacionUsaLaDistanciaPorCarretera() {
        Session pyme = register("PYME", "Rutas Cotiza");
        // Cusco → Abancay: 208.1 km por carretera (antes, línea recta × 1.35 = 134 km).
        as(pyme).body(Map.of("originCity", "CUSCO", "destinationCity", "ABANCAY", "weightKg", 10000, "cargoType", "GENERAL"))
                .post("/api/v1/pricing/calculate").then().statusCode(200)
                .body("data.distanceKm", num(208.1))
                .body("data.fuelCost", num(135.27))          // 208.1 × 0.65 = 135.265 → HALF_UP
                .body("data.tollCost", num(24));             // ceil(208.1 / 120) = 2 × 12
    }
}
