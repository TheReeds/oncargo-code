package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Roadmap 2.8: mejorar la oferta (solo bajar, o cambiar de vehículo), posición aproximada sin montos ajenos (RN04) y
 * mediana por ruta solo con k >= 5, sin montos individuales.
 */
@QuarkusTest
class BidImprovementFlowTest {

    private static String bidPath(String id) { return "/api/v1/bids/" + id; }

    @Test
    void mejorarSoloBajaElMontoYLaPymeVeLaMarca() {
        var b = Flows.bidding("Mejora");
        double original = b.bidAmount();
        // Bajar: ok, queda el historial y la PYME ve «mejorada»
        as(b.carrier()).body(Map.of("amount", original - 50)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(200)
                .body("data.amount", num(original - 50)).body("data.improvedCount", is(1))
                .body("data.improvedAt", notNullValue()).body("data.status", is("PENDING"));
        as(b.pyme()).get("/api/v1/shipment-requests/" + b.requestId() + "/bids").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].improvedCount", is(1)).body("data[0].amount", num(original - 50));
        as(b.carrier()).get(bidPath(b.bidId()) + "/revisions").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].previousAmount", num(original))
                .body("data[0].newAmount", num(original - 50));

        // Subir el monto: nunca
        as(b.carrier()).body(Map.of("amount", original + 10)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(400).body("error.code", is("LOGYX-BID-006"));
        // Igual y sin cambiar nada: tampoco
        as(b.carrier()).body(Map.of("amount", original - 50)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(400).body("error.code", is("LOGYX-BID-007"));
        as(b.carrier()).body(Map.of()).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(400).body("error.code", is("LOGYX-BID-007"));
        // Bajo el piso (RN03)
        as(b.carrier()).body(Map.of("amount", b.floor() - 100)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(400).body("error.code", is("LOGYX-BID-001"));
        // Una mejora no es una oferta nueva: seguir sin poder ofertar otra vez (BID-003)
        as(b.carrier()).body(Map.of("vehicleId", b.vehicleId(), "amount", original))
                .post("/api/v1/shipment-requests/" + b.requestId() + "/bids").then().statusCode(409)
                .body("error.code", is("LOGYX-BID-003"));
    }

    @Test
    void cambiarDeVehiculoExigeUnoApto() {
        var b = Flows.bidding("MejoraVeh");
        String small = vehicleOf(b.carrier(), "VAN", 500, null);        // 2000 kg no caben
        String ok = vehicleOf(b.carrier(), "TRUCK_LARGE", 20000, null);
        as(b.carrier()).body(Map.of("vehicleId", small)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-001"));
        as(b.carrier()).body(Map.of("vehicleId", ok)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(200).body("data.vehicleId", is(ok)).body("data.vehicleType", is("TRUCK_LARGE"))
                .body("data.improvedCount", is(1));
        // Vehículo de otra organización: 404
        Session other = register("CARRIER", "Mejora Ajeno");
        String foreign = vehicle(other, plate(), 8000);
        as(b.carrier()).body(Map.of("vehicleId", foreign)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(404);
    }

    @Test
    void soloElDuenoMejoraYVeElHistorialYSoloConLaSubastaAbierta() {
        var b = Flows.bidding("MejoraAjena");
        Session other = register("CARRIER", "Mejora Intrusa");
        as(other).body(Map.of("amount", b.bidAmount() - 10)).post(bidPath(b.bidId()) + "/improve").then().statusCode(404);
        as(other).get(bidPath(b.bidId()) + "/revisions").then().statusCode(404);
        as(b.pyme()).body(Map.of("amount", b.bidAmount() - 10)).post(bidPath(b.bidId()) + "/improve").then().statusCode(403);
        as(b.pyme()).get(bidPath(b.bidId()) + "/revisions").then().statusCode(403);
        // Adjudicada: ya no se mejora
        as(b.pyme()).post(bidPath(b.bidId()) + "/accept").then().statusCode(200);
        as(b.carrier()).body(Map.of("amount", b.bidAmount() - 10)).post(bidPath(b.bidId()) + "/improve")
                .then().statusCode(409);
    }

    @Test
    void laPosicionEsCualitativaYNuncaTraeMontosAjenos() {
        Session pyme = register("PYME", "Posicion Pyme");
        var req = publish(pyme, 1500).statusCode(201).extract();
        String requestId = req.path("data.id");
        float floor = req.path("data.priceFloor");
        // 4 transportistas ofertan de menor a mayor (con 5 la subasta cerraría por RN04); el último mejora y sube de lugar
        List<Session> carriers = new java.util.ArrayList<>();
        List<String> bids = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Session c = register("CARRIER", "Posicion C" + i);
            carriers.add(c);
            String v = vehicle(c, plate(), 8000);
            bids.add(as(c).body(Map.of("vehicleId", v, "amount", (float) Math.ceil(floor) + 100 + i * 100))
                    .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201).extract().path("data.id"));
        }
        Session last = carriers.get(3);
        var before = as(last).get("/api/v1/shipment-requests/" + requestId).then().statusCode(200)
                .body("data.myBidStanding.rank", is(4)).body("data.myBidStanding.position", is("UPPER_HALF"))
                .extract().asString();
        as(carriers.get(1)).get("/api/v1/shipment-requests/" + requestId).then()
                .body("data.myBidStanding.rank", is(2)).body("data.myBidStanding.position", is("TOP_TWO"));
        as(carriers.get(2)).get("/api/v1/shipment-requests/" + requestId).then()
                .body("data.myBidStanding.rank", is(3)).body("data.myBidStanding.position", is("UPPER_HALF"));

        as(last).body(Map.of("amount", (float) Math.ceil(floor) + 50)).post(bidPath(bids.get(3)) + "/improve")
                .then().statusCode(200);
        String after = as(last).get("/api/v1/shipment-requests/" + requestId).then().statusCode(200)
                .body("data.myBidStanding.rank", is(1)).body("data.myBidStanding.position", is("TOP_TWO"))
                .body("data.myBidStanding.isBest", is(true)).extract().asString();
        // Ni la respuesta antes ni después trae los montos de los demás
        for (int i = 0; i < 3; i++) {
            String theirs = String.valueOf((long) Math.ceil(floor) + 100 + i * 100);
            assertFalse(after.contains(":" + theirs + ".0") || before.contains(":" + theirs + ".0"),
                    "el transportista no debe ver el monto ajeno " + theirs);
        }
        // La mejora no cuenta como oferta nueva: el conteo sigue en 4
        as(last).get("/api/v1/shipment-requests/" + requestId).then().body("data.myBidStanding.bidCount", is(4));
    }

    @Test
    void laMedianaPorRutaSoloApareceConAlMenosCincoEnviosYSinMontosIndividuales() {
        // Ruta propia de este test (TACNA → MOQUEGUA): 5 adjudicaciones de 5 transportistas distintos
        Session carrierForStats = register("CARRIER", "Stats Carrier 0");
        Session pyme = register("PYME", "Stats Pyme");
        String probe = publishRoute(pyme, "TACNA", "MOQUEGUA", 800, 12).statusCode(201).extract().path("data.id");
        String untouched = publishRoute(pyme, "ABANCAY", "CUSCO", 800, 12).statusCode(201).extract().path("data.id");
        as(carrierForStats).get("/api/v1/shipment-requests/" + untouched + "/lane-stats").then().statusCode(200)
                .body("data", nullValue());                           // sin historial (k < 5): no se muestra nada
        as(pyme).body(Map.of()).post("/api/v1/shipment-requests/" + untouched + "/cancel").then().statusCode(200);

        List<Integer> prices = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Session c = register("CARRIER", "Stats Carrier " + (i + 1));   // 5 transportistas distintos
            String v = vehicle(c, plate(), 8000);
            var r = publishRoute(pyme, "TACNA", "MOQUEGUA", 800, 12 + i).statusCode(201).extract();
            String rid = r.path("data.id");
            float floor = r.path("data.priceFloor");
            int amount = (int) Math.ceil(floor) + 101 + i * 37;
            if (amount % 5 == 0) amount++;                          // ningún monto individual es múltiplo de 5 (la mediana sí)
            prices.add(amount);
            String bid = as(c).body(Map.of("vehicleId", v, "amount", amount))
                    .post("/api/v1/shipment-requests/" + rid + "/bids").then().statusCode(201).extract().path("data.id");
            as(pyme).post("/api/v1/bids/" + bid + "/accept").then().statusCode(200);
        }
        String body = as(carrierForStats).get("/api/v1/shipment-requests/" + probe + "/lane-stats").then().statusCode(200)
                .body("data.samples", greaterThanOrEqualTo(5)).body("data.windowDays", is(90))
                .body("data.median", notNullValue()).extract().asString();
        float median = from(body).getFloat("data.median");
        assertEquals(0, median % 5, 0.001, "la mediana se redondea a S/ 5");
        // Ningún monto individual aparece en la respuesta
        for (int p : prices) assertFalse(body.contains(":" + p + ".0") || body.contains(":" + p + ","), "monto individual " + p);
        // El transportista ni siquiera ve las ofertas ajenas por la API
        as(carrierForStats).get("/api/v1/shipment-requests/" + probe + "/bids").then().statusCode(403);
    }

    private static io.restassured.path.json.JsonPath from(String json) {
        return io.restassured.path.json.JsonPath.from(json);
    }
}
