package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Ofertas selladas (RN04) en las lecturas: posición y banda del transportista sin montos ajenos, mejor oferta solo
 * para la PYME dueña, ofertas de una solicitud para el operador, y F15 (solicitud perdida o cancelada sigue visible
 * para quien ofertó).
 */
@QuarkusTest
class SealedBidsFlowTest {

    private record Carrier(Session session, String vehicleId, String name) {}

    private Carrier carrier(String name) {
        Session s = register("CARRIER", name);
        return new Carrier(s, vehicle(s, plate(), 8000), name);
    }

    private String bid(Carrier c, String requestId, int amount) {
        return as(c.session()).body(Map.of("vehicleId", c.vehicleId(), "amount", amount))
                .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201).extract().path("data.id");
    }

    private JsonPath myBidsOf(Carrier c) {
        return as(c.session()).get("/api/v1/shipment-requests/my-bids").then().statusCode(200).extract().jsonPath();
    }

    private String myBidsBody(Carrier c) {
        return as(c.session()).get("/api/v1/shipment-requests/my-bids").then().statusCode(200).extract().asString();
    }

    private static String standing(String field) {
        return "data.find { it.id == '%s' }.myBidStanding." + field;
    }

    /** El JSON no contiene ninguno de los montos como número (con o sin decimales). */
    private static void assertNoAmounts(String json, String where, int... amounts) {
        for (int a : amounts) {
            assertFalse(Pattern.compile("(?<![\\d.])" + a + "(\\.0+)?(?![\\d])").matcher(json).find(),
                    where + " filtra el monto " + a + ": " + json);
        }
    }

    @Test
    void posicionYBandaSinRevelarMontosAjenos() {
        Session pyme = register("PYME", "Sealed Pyme");
        Carrier a = carrier("Sealed Alfa"), b = carrier("Sealed Beta"), c = carrier("Sealed Gamma"),
                d = carrier("Sealed Delta"), e = carrier("Sealed Epsilon");
        var req = publish(pyme, 2000).statusCode(201).extract();
        String rid = req.path("data.id");
        int best = (int) Math.ceil(((Number) req.path("data.suggestedPrice")).doubleValue() * 1.5);
        int second = (int) Math.round(best * 1.04), third = (int) Math.round(best * 1.10),
                fourth = (int) Math.round(best * 1.30);

        String bidA = bid(a, rid, best);
        bid(b, rid, second);
        bid(c, rid, third);
        bid(d, rid, fourth);

        // En vivo: rank, cantidad y banda cualitativa (no es la posición final)
        JsonPath ja = myBidsOf(a), jb = myBidsOf(b), jc = myBidsOf(c), jd = myBidsOf(d);
        assertEquals(1, ja.getInt(standing("rank").formatted(rid)));
        assertEquals(4, ja.getInt(standing("bidCount").formatted(rid)));
        assertTrue(ja.getBoolean(standing("isBest").formatted(rid)));
        assertEquals("BEST", ja.getString(standing("vsBest").formatted(rid)));
        assertFalse(ja.getBoolean(standing("finalStanding").formatted(rid)));
        assertEquals(2, jb.getInt(standing("rank").formatted(rid)));
        assertFalse(jb.getBoolean(standing("isBest").formatted(rid)));
        assertEquals("WITHIN_5_PCT", jb.getString(standing("vsBest").formatted(rid)));
        assertEquals(3, jc.getInt(standing("rank").formatted(rid)));
        assertEquals("WITHIN_15_PCT", jc.getString(standing("vsBest").formatted(rid)));
        assertEquals(4, jd.getInt(standing("rank").formatted(rid)));
        assertEquals("ABOVE_15_PCT", jd.getString(standing("vsBest").formatted(rid)));

        // Sin fuga: ni my-bids, ni el marketplace, ni el detalle del transportista traen montos ajenos ni la mejor oferta
        for (Carrier x : List.of(b, c, d)) {
            int mine = x == b ? second : x == c ? third : fourth;
            int[] others = java.util.stream.IntStream.of(best, second, third, fourth).filter(v -> v != mine).toArray();
            assertNoAmounts(myBidsBody(x), "my-bids", others);
            assertNoAmounts(as(x.session()).get("/api/v1/shipment-requests?size=100").then().statusCode(200)
                    .extract().asString(), "marketplace", others);
            var detail = as(x.session()).get("/api/v1/shipment-requests/" + rid).then().statusCode(200)
                    .body("data.bestBidAmount", nullValue()).body("data.bestBidCarrierName", nullValue())
                    .body("data.myBidStanding.rank", greaterThan(1)).extract().asString();
            assertNoAmounts(detail, "detalle", others);
            assertFalse(detail.contains("Sealed Alfa"), "el detalle nombra a otro transportista");
        }
        // El marketplace también trae la posición del que ofertó, y nada para quien no ofertó
        as(a.session()).get("/api/v1/shipment-requests?size=100").then().statusCode(200)
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.rank", is(1));
        as(e.session()).get("/api/v1/shipment-requests?size=100").then().statusCode(200)
                .body("data.find { it.id == '" + rid + "' }.myBidStanding", nullValue())
                .body("data.find { it.id == '" + rid + "' }.myBid", nullValue());

        // La PYME dueña ve la mejor oferta pendiente (misma consulta que la lista)
        as(pyme).get("/api/v1/shipment-requests/mine").then().statusCode(200)
                .body("data.find { it.id == '" + rid + "' }.bestBidAmount", num(best))
                .body("data.find { it.id == '" + rid + "' }.bestBidCarrierName", is("Sealed Alfa"))
                .body("data.find { it.id == '" + rid + "' }.bestBidCarrierTrustScore", notNullValue())
                .body("data.find { it.id == '" + rid + "' }.myBidStanding", nullValue());
        as(pyme).get("/api/v1/shipment-requests/mine?page=0&size=5").then().statusCode(200)
                .body("data.find { it.id == '" + rid + "' }.bestBidAmount", num(best));
        as(pyme).get("/api/v1/shipment-requests/" + rid).then().body("data.bestBidAmount", num(best));
        // Ofertas retiradas no cuentan: Alfa retira y la mejor pasa a Beta
        as(a.session()).post("/api/v1/bids/" + bidA + "/withdraw").then().statusCode(200);
        as(pyme).get("/api/v1/shipment-requests/mine").then()
                .body("data.find { it.id == '" + rid + "' }.bestBidAmount", num(second))
                .body("data.find { it.id == '" + rid + "' }.bestBidCarrierName", is("Sealed Beta"));
        // Alfa retiró: conserva la solicitud en my-bids con su oferta WITHDRAWN y sin posición
        as(a.session()).get("/api/v1/shipment-requests/my-bids").then()
                .body("data.find { it.id == '" + rid + "' }.myBid.status", is("WITHDRAWN"))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding", nullValue());
        // Beta es ahora la mejor
        as(b.session()).get("/api/v1/shipment-requests/my-bids").then()
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.rank", is(1))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.bidCount", is(3))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.vsBest", is("BEST"));
    }

    @Test
    void cierrePorMaximoDeOfertasDejaPosicionFinalEmpateYMotivo() {
        Session pyme = register("PYME", "Sealed Cierre Pyme");
        Carrier a = carrier("Cierre Uno"), b = carrier("Cierre Dos"), c = carrier("Cierre Tres"),
                d = carrier("Cierre Cuatro"), e = carrier("Cierre Cinco");
        var req = publish(pyme, 2000).statusCode(201).extract();
        String rid = req.path("data.id");
        int best = (int) Math.ceil(((Number) req.path("data.suggestedPrice")).doubleValue() * 1.5);
        bid(a, rid, best);
        bid(b, rid, best + 10);
        bid(c, rid, best + 20);
        bid(d, rid, best * 2);
        bid(e, rid, best);                        // empate con Uno: la más antigua va primero; 5.ª oferta cierra (RN04)

        // Posición final: Uno 1.º, Cinco 2.º (banda BEST por empate), Cuatro último
        JsonPath ja = myBidsOf(a), je = myBidsOf(e), jd = myBidsOf(d);
        assertEquals(1, ja.getInt(standing("rank").formatted(rid)));
        assertEquals(5, ja.getInt(standing("bidCount").formatted(rid)));
        assertTrue(ja.getBoolean(standing("finalStanding").formatted(rid)));
        assertEquals(2, je.getInt(standing("rank").formatted(rid)));
        assertFalse(je.getBoolean(standing("isBest").formatted(rid)));
        assertEquals("BEST", je.getString(standing("vsBest").formatted(rid)));
        assertEquals(5, jd.getInt(standing("rank").formatted(rid)));
        assertEquals("ABOVE_15_PCT", jd.getString(standing("vsBest").formatted(rid)));
        // F51: el motivo y la fecha de cierre llegan en la subasta
        assertEquals("CLOSED", ja.getString("data.find { it.id == '%s' }.auction.status".formatted(rid)));
        assertEquals("MAX_BIDS", ja.getString("data.find { it.id == '%s' }.auction.closeReason".formatted(rid)));
        assertNotNull(ja.getString("data.find { it.id == '%s' }.auction.closedAt".formatted(rid)));
        assertNoAmounts(myBidsBody(d), "my-bids de Cuatro", best + 10, best + 20, best);

        // La PYME: mejor oferta pendiente = la más antigua del empate
        as(pyme).get("/api/v1/shipment-requests/mine").then()
                .body("data.find { it.id == '" + rid + "' }.bestBidCarrierName", is("Cierre Uno"))
                .body("data.find { it.id == '" + rid + "' }.auction.closeReason", is("MAX_BIDS"));

        // Adjudica a Dos (no la mejor): el ganador no se revela al resto
        String winning = as(pyme).get("/api/v1/shipment-requests/" + rid + "/bids").then().statusCode(200)
                .body("data", hasSize(5)).body("data.amount", everyItem(notNullValue()))
                .extract().path("data.find { it.carrierName == 'Cierre Dos' }.id");
        String winnerOrg = b.session().orgId();
        as(pyme).post("/api/v1/bids/" + winning + "/accept").then().statusCode(200);
        as(pyme).get("/api/v1/shipment-requests/mine").then()
                .body("data.find { it.id == '" + rid + "' }.status", is("MATCHED"))
                .body("data.find { it.id == '" + rid + "' }.bestBidAmount", nullValue());

        // F15: Uno perdió y la solicitud sigue en my-bids con su oferta REJECTED y el estado de la solicitud
        var lost = as(a.session()).get("/api/v1/shipment-requests/my-bids").then().statusCode(200)
                .body("data.find { it.id == '" + rid + "' }.status", is("MATCHED"))
                .body("data.find { it.id == '" + rid + "' }.myBid.status", is("REJECTED"))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.rank", is(1))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.finalStanding", is(true))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.vsBest", nullValue())
                .extract().asString();
        // El detalle ya no da 404 a quien ofertó y no revela al ganador ni su monto
        String detail = as(a.session()).get("/api/v1/shipment-requests/" + rid).then().statusCode(200)
                .body("data.status", is("MATCHED")).body("data.myBid.status", is("REJECTED")).extract().asString();
        for (String body : List.of(lost, detail)) {
            assertFalse(body.contains("Cierre Dos"), "revela el nombre del ganador");
            assertFalse(body.contains(winnerOrg), "revela la organización ganadora");
            assertNoAmounts(body, "vista del perdedor", best + 10, best + 20, best * 2);
        }
        as(b.session()).get("/api/v1/shipment-requests/my-bids").then()
                .body("data.find { it.id == '" + rid + "' }.myBid.status", is("ACCEPTED"));
        // Quien nunca ofertó no la ve (RLS: ni lista ni detalle)
        Carrier outsider = carrier("Cierre Ajeno");
        as(outsider.session()).get("/api/v1/shipment-requests/" + rid).then().statusCode(404)
                .body("error.message", is("Solicitud no encontrada"));
        as(outsider.session()).get("/api/v1/shipment-requests/my-bids").then().body("data", empty());
    }

    @Test
    void solicitudCanceladaSigueVisibleParaQuienOferto() {
        Session pyme = register("PYME", "Sealed Cancel Pyme");
        Carrier a = carrier("Cancel Alfa"), outsider = carrier("Cancel Ajeno");
        var req = publish(pyme, 2000).statusCode(201).extract();
        String rid = req.path("data.id");
        bid(a, rid, (int) Math.ceil(((Number) req.path("data.suggestedPrice")).doubleValue()) + 50);
        as(pyme).body(Map.of("reason", "Cambio de planes")).post("/api/v1/shipment-requests/" + rid + "/cancel")
                .then().statusCode(200);

        as(a.session()).get("/api/v1/shipment-requests/my-bids").then().statusCode(200)
                .body("data.find { it.id == '" + rid + "' }.status", is("CANCELLED"))
                .body("data.find { it.id == '" + rid + "' }.myBid.status", is("REJECTED"))
                .body("data.find { it.id == '" + rid + "' }.auction.status", is("CANCELLED"))
                .body("data.find { it.id == '" + rid + "' }.auction.closeReason", is("CANCELLED"))
                .body("data.find { it.id == '" + rid + "' }.myBidStanding.finalStanding", is(true));
        as(a.session()).get("/api/v1/shipment-requests/" + rid).then().statusCode(200)
                .body("data.status", is("CANCELLED")).body("data.cancelReason", is("Cambio de planes"));
        as(outsider.session()).get("/api/v1/shipment-requests/" + rid).then().statusCode(404);
    }

    @Test
    void elOperadorLeeLasOfertasDeCualquierSolicitud() {
        Session pyme = register("PYME", "Sealed Op Pyme");
        Carrier a = carrier("Op Alfa"), b = carrier("Op Beta");
        var req = publish(pyme, 2000).statusCode(201).extract();
        String rid = req.path("data.id");
        int base = (int) Math.ceil(((Number) req.path("data.suggestedPrice")).doubleValue());
        bid(b, rid, base + 200);
        bid(a, rid, base + 100);
        Session op = operatorSession();

        as(op).get("/api/v1/shipment-requests/" + rid + "/bids").then().statusCode(200)
                .body("data", hasSize(2))
                .body("data.carrierName", contains("Op Alfa", "Op Beta"))      // menor monto primero
                .body("data[0].amount", num(base + 100)).body("data[1].amount", num(base + 200))
                .body("data[0].carrierTrustScore", notNullValue()).body("data[0].carrierRucVerified", is(true))
                .body("data[0].vehiclePlate", notNullValue()).body("data[0].status", is("PENDING"));
        as(op).get("/api/v1/shipment-requests/" + java.util.UUID.randomUUID() + "/bids").then().statusCode(404);
        // Los demás siguen igual: otra PYME 404, transportistas 403
        as(register("PYME", "Sealed Op Ajena")).get("/api/v1/shipment-requests/" + rid + "/bids").then().statusCode(404);
        as(a.session()).get("/api/v1/shipment-requests/" + rid + "/bids").then().statusCode(403);
        // Y no ve la mejor oferta en RequestView (solo la PYME dueña)
        as(op).get("/api/v1/shipment-requests/" + rid).then().statusCode(200).body("data.bestBidAmount", nullValue());
    }

    private Session operatorSession() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var auth = io.quarkus.arc.Arc.container().instance(pe.logyx.identity.application.AuthService.class).get();
        var r = auth.provisionOperator("LOGYX Ofertas " + System.nanoTime() % 1000, newRuc(), "Operador Test", email,
                "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }
}
