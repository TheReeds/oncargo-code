package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** Bandeja de ofertas consolidada y acciones masivas de «Mis solicitudes» (2.10). */
@QuarkusTest
class RequestBatchFlowTest {

    private static final String B = "/api/v1/shipment-requests/batch";

    private static String req(String id) { return "/api/v1/shipment-requests/" + id; }

    @Test
    void laBandejaTraeUnaFilaPorSolicitudConLaMejorPorMontoYPorTrust() {
        Session pyme = register("PYME", "Bandeja Pyme");
        Session a = register("CARRIER", "Bandeja Alfa");
        Session b = register("CARRIER", "Bandeja Beta");
        String va = vehicle(a, plate(), 8000), vb = vehicle(b, plate(), 8000);
        String r1 = publish(pyme, 2000).statusCode(201).extract().path("data.id");
        String r2 = publish(pyme, 2500).statusCode(201).extract().path("data.id");
        publish(pyme, 3000).statusCode(201);                                          // sin ofertas: no sale
        bid(a, r1, va, 400);
        String cheap = bid(b, r1, vb, 100);
        bid(a, r2, va, 200);
        as(pyme).body(Map.of("kind", "PREFERRED")).put("/api/v1/carrier-preferences/" + b.orgId()).then().statusCode(200);

        var inbox = as(pyme).get("/api/v1/offers/inbox").then().statusCode(200)
                .body("data.requests", is(2), "data.bids", is(3), "data.rows", hasSize(2)).extract();
        // Ordenadas por recojo; buscamos por id
        List<Map<String, Object>> rows = inbox.path("data.rows");
        Map<String, Object> row1 = rows.stream().filter(r -> r1.equals(r.get("requestId"))).findFirst().orElseThrow();
        assertEquals(2, row1.get("bidCount"));
        @SuppressWarnings("unchecked") Map<String, Object> best = (Map<String, Object>) row1.get("best");
        assertEquals(cheap, best.get("bidId"));
        assertEquals(true, best.get("preferred"));
        assertNotNull(row1.get("closesAt"));
        assertNotNull(row1.get("bestTrust"));

        // Otra PYME y los transportistas no la ven
        as(register("PYME", "Bandeja Ajena")).get("/api/v1/offers/inbox").then().body("data.rows", hasSize(0));
        as(a).get("/api/v1/offers/inbox").then().statusCode(403);
        // «Aceptar la mejor» sigue siendo el endpoint de siempre
        as(pyme).post("/api/v1/bids/" + cheap + "/accept").then().statusCode(200);
        as(pyme).get("/api/v1/offers/inbox").then().body("data.requests", is(1));
    }

    @Test
    void cancelarVariasConUnMotivoComunDaElResultadoPorItem() {
        Session pyme = register("PYME", "Lote Cancela");
        String r1 = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        String r2 = publish(pyme, 1100).statusCode(201).extract().path("data.id");
        String ghost = UUID.randomUUID().toString();

        as(pyme).body(Map.of("ids", List.of(r1, r2, ghost, r1), "reason", "Cambió el pedido")).post(B + "/cancel")
                .then().statusCode(200)
                .body("data.total", is(3), "data.succeeded", is(2), "data.failed", is(1),
                        "data.items.find { it.requestId == '" + ghost + "' }.code", is("LOGYX-GEN-404"),
                        "data.items.find { it.requestId == '" + r1 + "' }.ok", is(true));
        as(pyme).get(req(r1)).then().body("data.status", is("CANCELLED"), "data.cancelReason", is("Cambió el pedido"));
        // No es atómico: repetir deja a las ya canceladas con su código
        as(pyme).body(Map.of("ids", List.of(r1))).post(B + "/cancel").then().statusCode(200)
                .body("data.failed", is(1), "data.items[0].code", is("LOGYX-REQ-002"));
        // El motivo respeta RN07
        String r3 = publish(pyme, 1200).statusCode(201).extract().path("data.id");
        as(pyme).body(Map.of("ids", List.of(r3), "reason", "Llámame al 987654321")).post(B + "/cancel").then().statusCode(200)
                .body("data.items[0].ok", is(false), "data.items[0].code", is("LOGYX-NEG-001"));
        // Otra PYME no cancela las tuyas
        as(register("PYME", "Lote Ajena")).body(Map.of("ids", List.of(r3))).post(B + "/cancel").then().statusCode(200)
                .body("data.items[0].code", is("LOGYX-GEN-404"));
        as(pyme).get(req(r3)).then().body("data.status", is("PUBLISHED"));
    }

    @Test
    void validaElLoteYElRol() {
        Session pyme = register("PYME", "Lote Valida");
        as(pyme).body(Map.of("ids", List.of())).post(B + "/cancel").then().statusCode(400);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) many.add(UUID.randomUUID().toString());
        as(pyme).body(Map.of("ids", many)).post(B + "/cancel").then().statusCode(400);
        as(register("CARRIER", "Lote Carrier")).body(Map.of("ids", List.of(UUID.randomUUID().toString())))
                .post(B + "/cancel").then().statusCode(403);
    }

    @Test
    void reprogramarSoloSinOfertasPendientes() {
        Session pyme = register("PYME", "Lote Reprograma");
        Session carrier = register("CARRIER", "Lote Reprograma SRL");
        String free = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        String withBid = publish(pyme, 1100).statusCode(201).extract().path("data.id");
        bid(carrier, withBid, vehicle(carrier, plate(), 8000), 100);
        String when = LocalDate.now().plusDays(9).toString();

        as(pyme).body(Map.of("ids", List.of(free, withBid), "pickupDate", when)).post(B + "/reschedule").then().statusCode(200)
                .body("data.succeeded", is(1), "data.failed", is(1),
                        "data.items.find { it.requestId == '" + withBid + "' }.code", is("LOGYX-REQ-009"));
        as(pyme).get(req(free)).then().body("data.pickupDate", is(when));
        as(pyme).get(req(withBid)).then().body("data.pickupDate", is(LocalDate.now().plusDays(2).toString()));
        as(pyme).body(Map.of("ids", List.of(free), "pickupDate", LocalDate.now().minusDays(1).toString()))
                .post(B + "/reschedule").then().statusCode(400);
        as(pyme).body(Map.of("ids", List.of(free))).post(B + "/reschedule").then().statusCode(400);
    }

    @Test
    void duplicarParaLaSemanaSiguienteConIdempotencia() {
        Session pyme = register("PYME", "Lote Duplica");
        String r1 = publish(pyme, 1000).statusCode(201).extract().path("data.id");        // recojo en 2 días
        String expected = LocalDate.now().plusDays(9).toString();
        String key = "lote-" + UUID.randomUUID().toString().substring(0, 8);

        var first = as(pyme).header("Idempotency-Key", key).body(Map.of("ids", List.of(r1))).post(B + "/duplicate").then()
                .statusCode(200).body("data.succeeded", is(1), "data.items[0].resultCode", startsWith("OC-"))
                .extract();
        String copy = first.path("data.items[0].resultId");
        assertNotEquals(r1, copy);
        as(pyme).get(req(copy)).then().body("data.status", is("PUBLISHED"), "data.pickupDate", is(expected),
                "data.weightKg", num(1000), "data.republishedFrom", nullValue());
        // Reintentar el lote con la misma clave no duplica
        as(pyme).header("Idempotency-Key", key).body(Map.of("ids", List.of(r1))).post(B + "/duplicate").then()
                .statusCode(200).body("data.items[0].resultId", is(copy));
        as(pyme).get("/api/v1/shipment-requests/mine").then().body("data", hasSize(2));
        // Sin clave se crea otra
        as(pyme).body(Map.of("ids", List.of(r1))).post(B + "/duplicate").then().statusCode(200)
                .body("data.items[0].resultId", not(copy));
        as(pyme).header("Idempotency-Key", "x".repeat(60)).body(Map.of("ids", List.of(r1))).post(B + "/duplicate")
                .then().statusCode(400);
    }
}
