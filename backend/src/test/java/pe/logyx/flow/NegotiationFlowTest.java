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

/** Chat por solicitud (RF25, RN07) y contra-ofertas (RF27, RF28). */
@QuarkusTest
class NegotiationFlowTest {

    private static String thread(String requestId, String carrierOrgId) {
        return "/api/v1/shipment-requests/" + requestId + "/threads/" + carrierOrgId + "/messages";
    }

    @Test
    void chatEntrePymeYTransportistaConBloqueoDeContactosYCursor() {
        Session pyme = register("PYME", "Chat Pyme SAC");
        Session carrier = register("CARRIER", "Chat Carrier SRL");
        Session other = register("CARRIER", "Chat Intruso SRL");
        String requestId = publish(pyme, 1500).statusCode(201).extract().path("data.id");
        String path = thread(requestId, carrier.orgId());

        // La PYME no puede abrir un hilo con quien no ofertó ni escribió
        as(pyme).body(Map.of("body", "Hola")).post(path).then().statusCode(404);

        // El transportista pregunta antes de ofertar
        as(carrier).body(Map.of("body", "¿La carga está paletizada? Puedo recoger el 15/11 a las 6:00, S/ 1,850.00"))
                .post(path).then().statusCode(201)
                .body("data.mine", is(true)).body("data.senderName", is("Chat Carrier SRL"))
                .body("data.system", is(false));

        // RN07: teléfonos (con o sin separadores, +51), emails, URLs y usuarios → LOGYX-NEG-001, field body
        for (String blocked : List.of("Llámame al 987654321", "mi cel 987 654 321", "987-654-321 whatsapp",
                "+51 987 654 321", "escríbeme a ventas@transportes.pe", "mira https://mi-web.com/tarifas",
                "www.transportes.com", "transportessur.com.pe", "wa.me/51987654321", "sígueme @transportes_sur")) {
            as(carrier).body(Map.of("body", blocked)).post(path).then().statusCode(400)
                    .body("error.code", is("LOGYX-NEG-001")).body("error.field", is("body"));
        }

        // La PYME ve el hilo y responde
        as(pyme).get("/api/v1/shipment-requests/" + requestId + "/threads").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].carrierOrgId", is(carrier.orgId()))
                .body("data[0].messageCount", is(1)).body("data[0].bidId", nullValue());
        as(pyme).body(Map.of("body", "Sí, 12 pallets.")).post(path).then().statusCode(201)
                .body("data.mine", is(true));

        // Otro transportista no ve ni escribe en el hilo ajeno; su propio hilo está vacío
        as(other).get(path).then().statusCode(404);
        as(other).body(Map.of("body", "hola")).post(path).then().statusCode(404);
        as(other).get("/api/v1/shipment-requests/" + requestId + "/threads").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].messageCount", is(0));

        // Historial por cursor, en orden cronológico
        for (int i = 1; i <= 4; i++) as(carrier).body(Map.of("body", "mensaje " + i)).post(path).then().statusCode(201);
        var page1 = as(pyme).get(path + "?size=4").then().statusCode(200)
                .body("data.body", contains("mensaje 1", "mensaje 2", "mensaje 3", "mensaje 4"))
                .body("data[0].mine", is(false))
                .body("meta.nextCursor", notNullValue()).extract();
        String cursor = page1.path("meta.nextCursor");
        as(pyme).get(path + "?size=4&before=" + cursor).then().statusCode(200)
                .body("data", hasSize(2)).body("data[1].body", is("Sí, 12 pallets."))
                .body("data[1].mine", is(true)).body("meta.nextCursor", nullValue());
        as(pyme).get(path + "?size=0").then().statusCode(400).body("error.field", is("size"));

        // Notificación de mensaje nuevo a la otra parte
        as(pyme).get("/api/v1/notifications?unread=true").then().statusCode(200)
                .body("data.type", hasItem("NEW_MESSAGE"));
        as(carrier).get("/api/v1/notifications").then().body("data.type", hasItem("NEW_MESSAGE"));

        // Cancelada la solicitud, el hilo queda de solo lectura
        as(pyme).post("/api/v1/shipment-requests/" + requestId + "/cancel").then().statusCode(200);
        as(pyme).body(Map.of("body", "Gracias")).post(path).then().statusCode(409)
                .body("error.code", is("LOGYX-NEG-002"));
        as(pyme).get(path).then().statusCode(200);
    }

    @Test
    void contraOfertaAceptadaActualizaElMontoDeLaOferta() {
        Flows.Bidding b = Flows.bidding("Contra A");
        Session intruder = register("CARRIER", "Contra Intruso");
        String co = "/api/v1/bids/" + b.bidId() + "/counter-offers";

        // RN03 también aplica a la contra-oferta
        as(b.pyme()).body(Map.of("amount", b.floor() - 1)).post(co).then().statusCode(400)
                .body("error.code", is("LOGYX-BID-001")).body("error.field", is("amount"));
        // mismo monto → NEG-003 ; datos de contacto en el mensaje → NEG-001 (field message)
        as(b.pyme()).body(Map.of("amount", b.bidAmount())).post(co).then().statusCode(400)
                .body("error.code", is("LOGYX-NEG-003"));
        as(b.pyme()).body(Map.of("amount", b.above(50), "message", "llama al 951 222 333")).post(co).then()
                .statusCode(400).body("error.code", is("LOGYX-NEG-001")).body("error.field", is("message"));
        // un transportista ajeno no propone sobre esta oferta (RLS: no la ve)
        as(intruder).body(Map.of("amount", b.above(50))).post(co).then().statusCode(404);

        float proposed = b.above(100);
        String coId = as(b.pyme()).body(Map.of("amount", proposed, "message", "¿Lo dejamos en este monto?"))
                .post(co).then().statusCode(201)
                .body("data.status", is("PENDING")).body("data.previousAmount", is(b.bidAmount()))
                .extract().path("data.id");
        as(b.pyme()).body(Map.of("amount", proposed + 10)).post(co).then().statusCode(409)
                .body("error.code", is("LOGYX-NEG-004"));

        // El transportista recibe la notificación y la ve
        as(b.carrier()).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'COUNTER_OFFER' }.relatedId", is(coId));
        as(b.carrier()).get(co).then().statusCode(200).body("data.id", contains(coId));
        // Un tercero no puede responder ni ver
        as(intruder).post("/api/v1/counter-offers/" + coId + "/accept").then().statusCode(404);
        as(intruder).get(co).then().statusCode(404);
        as(b.pyme()).post("/api/v1/counter-offers/" + coId + "/accept").then().statusCode(403);

        as(b.carrier()).post("/api/v1/counter-offers/" + coId + "/accept").then().statusCode(200)
                .body("data.status", is("ACCEPTED")).body("data.respondedAt", notNullValue());
        as(b.carrier()).post("/api/v1/counter-offers/" + coId + "/accept").then().statusCode(409)
                .body("error.code", is("LOGYX-NEG-007"));

        // RF28: el monto de la oferta es ahora el negociado
        as(b.pyme()).get("/api/v1/shipment-requests/" + b.requestId() + "/bids").then()
                .body("data[0].amount", is(proposed));
        as(b.pyme()).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'COUNTER_OFFER' }.title", is("Contra-oferta aceptada"));
        // El chat registra los avisos del sistema
        as(b.pyme()).get(thread(b.requestId(), b.carrier().orgId())).then().statusCode(200)
                .body("data.system", everyItem(is(true))).body("data", hasSize(2))
                .body("data[0].proposedPrice", is(proposed));

        // Nueva contra-oferta y rechazo
        String co2 = as(b.pyme()).body(Map.of("amount", b.above(20))).post(co).then().statusCode(201)
                .extract().path("data.id");
        as(b.carrier()).post("/api/v1/counter-offers/" + co2 + "/reject").then().statusCode(200)
                .body("data.status", is("REJECTED"));
        as(b.pyme()).get("/api/v1/shipment-requests/" + b.requestId() + "/bids").then()
                .body("data[0].amount", is(proposed));

        // Aceptada la oferta, ya no se negocia
        as(b.pyme()).post("/api/v1/bids/" + b.bidId() + "/accept").then().statusCode(200);
        as(b.pyme()).body(Map.of("amount", b.above(30))).post(co).then().statusCode(409)
                .body("error.code", is("LOGYX-NEG-005"));
        // El hilo con el adjudicado sigue abierto
        as(b.carrier()).body(Map.of("body", "Confirmo recojo")).post(thread(b.requestId(), b.carrier().orgId()))
                .then().statusCode(201);
        assertNotNull(coId);
    }
}
