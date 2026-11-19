package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Transportistas preferidos y bloqueados e «invitar a ofertar» (2.9, V21). */
@QuarkusTest
class CarrierPreferencesFlowTest {

    private static final String PREFS = "/api/v1/carrier-preferences";

    @Test
    void bloquearEscondeLasSolicitudesImpideOfertarYBloqueaLaAceptacion() {
        Session pyme = register("PYME", "Bloqueo Pyme");
        Session blocked = register("CARRIER", "Bloqueado SRL");
        Session free = register("CARRIER", "Libre SRL");
        String vBlocked = vehicle(blocked, plate(), 8000);
        String vFree = vehicle(free, plate(), 8000);

        String r1 = publish(pyme, 2000).statusCode(201).extract().path("data.id");
        String bidOfBlocked = bid(blocked, r1, vBlocked, 100);        // ofertó antes de que lo bloquearan

        as(pyme).body(Map.of("kind", "BLOCKED")).put(PREFS + "/" + blocked.orgId()).then().statusCode(200)
                .body("data.kind", is("BLOCKED"), "data.carrierName", is("Bloqueado SRL"));
        as(pyme).get(PREFS).then().body("data", hasSize(1));
        as(pyme).get(PREFS + "?kind=PREFERRED").then().body("data", hasSize(0));

        // Las solicitudes nuevas de esa PYME no le aparecen ni existen para él (404), y no puede ofertar
        String r2 = publish(pyme, 2500).statusCode(201).extract().path("data.id");
        as(blocked).get("/api/v1/shipment-requests").then().statusCode(200)
                .body("data.id", not(hasItem(r2)));
        as(blocked).get("/api/v1/shipment-requests/" + r2).then().statusCode(404);
        as(blocked).body(Map.of("vehicleId", vBlocked, "amount", 99999)).post("/api/v1/shipment-requests/" + r2 + "/bids")
                .then().statusCode(404);
        as(free).get("/api/v1/shipment-requests").then().body("data.id", hasItem(r2));
        bid(free, r2, vFree, 100);

        // Su oferta de antes sigue legible para él, pero la PYME no puede aceptarla (BID-006) ni la ve en la bandeja
        as(blocked).get("/api/v1/shipment-requests/" + r1).then().statusCode(200);
        as(pyme).post("/api/v1/bids/" + bidOfBlocked + "/accept").then().statusCode(409)
                .body("error.code", is("LOGYX-BID-006"));
        as(pyme).get("/api/v1/offers/inbox").then().body("data.rows.requestId", not(hasItem(r1)))
                .body("data.rows.requestId", hasItem(r2));

        // Un transportista no lee las marcas (ni sabe que lo bloquearon) y solo una PYME las escribe
        as(blocked).get(PREFS).then().statusCode(403);
        as(blocked).body(Map.of("kind", "PREFERRED")).put(PREFS + "/" + free.orgId()).then().statusCode(403);

        // Quitar el bloqueo (idempotente) lo devuelve al marketplace
        as(pyme).delete(PREFS + "/" + blocked.orgId()).then().statusCode(200);
        as(pyme).delete(PREFS + "/" + blocked.orgId()).then().statusCode(200);
        as(blocked).get("/api/v1/shipment-requests/" + r2).then().statusCode(200);
        as(blocked).body(Map.of("vehicleId", vBlocked, "amount", 99999)).post("/api/v1/shipment-requests/" + r2 + "/bids")
                .then().statusCode(201);
    }

    @Test
    void validaQuienSeMarcaYAdmiteCambiarLaMarca() {
        Session pyme = register("PYME", "Marca Pyme");
        Session carrier = register("CARRIER", "Marcado SRL");
        Session otherPyme = register("PYME", "Marca Otra Pyme");
        as(pyme).body(Map.of("kind", "PREFERRED")).put(PREFS + "/" + otherPyme.orgId()).then().statusCode(400)
                .body("error.code", is("LOGYX-PRF-002"));
        as(pyme).body(Map.of("kind", "PREFERRED")).put(PREFS + "/" + UUID.randomUUID()).then().statusCode(404);
        as(pyme).body(Map.of()).put(PREFS + "/" + carrier.orgId()).then().statusCode(400);
        as(pyme).body(Map.of("kind", "PREFERRED")).put(PREFS + "/" + carrier.orgId()).then().statusCode(200)
                .body("data.kind", is("PREFERRED"));
        as(pyme).body(Map.of("kind", "PREFERRED")).put(PREFS + "/" + carrier.orgId()).then().statusCode(200);
        as(pyme).body(Map.of("kind", "BLOCKED")).put(PREFS + "/" + carrier.orgId()).then().statusCode(200)
                .body("data.kind", is("BLOCKED"));
        as(pyme).get(PREFS).then().body("data", hasSize(1), "data[0].kind", is("BLOCKED"));
        // Las marcas son de cada PYME: la otra no ve las de esta
        as(otherPyme).get(PREFS).then().body("data", hasSize(0));
    }

    @Test
    void invitarAOfertarAvisaSoloALosPreferidos() {
        Session pyme = register("PYME", "Invita Pyme");
        Session preferred = register("CARRIER", "Preferido SRL");
        Session plain = register("CARRIER", "Corriente SRL");
        as(pyme).body(Map.of("kind", "PREFERRED")).put(PREFS + "/" + preferred.orgId()).then().statusCode(200);

        // Al publicar con invitePreferred
        Map<String, Object> body = new HashMap<>(Map.of(
                "originCity", "LIMA", "originAddress", "Av. Argentina 1234, Callao",
                "destinationCity", "JULIACA", "destinationAddress", "Jr. San Román 456",
                "cargoType", "GENERAL", "cargoDescription", "Abarrotes", "weightKg", 1800,
                "pickupDate", LocalDate.now().plusDays(4).toString(), "invitePreferred", true));
        String requestId = as(pyme).body(body).post("/api/v1/shipment-requests").then().statusCode(201).extract().path("data.id");
        as(preferred).get("/api/v1/notifications").then().body(
                "data.find { it.type == 'REQUEST_INVITATION' }.title", is("Te invitan a ofertar"),
                "data.find { it.type == 'REQUEST_INVITATION' }.relatedId", is(requestId),
                "data.find { it.type == 'REQUEST_INVITATION' }.body", containsString("Invita Pyme"));
        as(plain).get("/api/v1/notifications").then().body("data.findAll { it.type == 'REQUEST_INVITATION' }", hasSize(0));

        // Sin invitación al publicar: se invita después
        String second = publish(pyme, 1500).statusCode(201).extract().path("data.id");
        as(preferred).get("/api/v1/notifications").then().body("data.findAll { it.type == 'REQUEST_INVITATION' }", hasSize(1));
        as(pyme).post("/api/v1/shipment-requests/" + second + "/invite-preferred").then().statusCode(200)
                .body("data.invited", is(1));
        as(preferred).get("/api/v1/notifications").then().body("data.findAll { it.type == 'REQUEST_INVITATION' }", hasSize(2));

        // Solo solicitudes publicadas y propias
        as(pyme).body(Map.of()).post("/api/v1/shipment-requests/" + second + "/cancel").then().statusCode(200);
        as(pyme).post("/api/v1/shipment-requests/" + second + "/invite-preferred").then().statusCode(409)
                .body("error.code", is("LOGYX-REQ-008"));
        as(plain).post("/api/v1/shipment-requests/" + requestId + "/invite-preferred").then().statusCode(403);
        as(register("PYME", "Invita Ajena")).post("/api/v1/shipment-requests/" + requestId + "/invite-preferred")
                .then().statusCode(404);
    }
}
