package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Centro de notificaciones (RF49/RF51) alimentado por eventos de dominio. */
@QuarkusTest
class NotificationFlowTest {

    @Test
    void ofertasGeneranNotificacionesQueSeMarcanComoLeidas() {
        Flows.Bidding b = Flows.bidding("Notif");
        Session loser = register("CARRIER", "Notif Perdedor");
        String v = vehicle(loser, plate(), 8000);
        as(loser).body(Map.of("vehicleId", v, "amount", b.above(400)))
                .post("/api/v1/shipment-requests/" + b.requestId() + "/bids").then().statusCode(201);

        // La PYME recibió dos NEW_BID, no leídas
        var list = as(b.pyme()).get("/api/v1/notifications").then().statusCode(200)
                .body("data", hasSize(2)).body("data.type", everyItem(is("NEW_BID")))
                .body("data.read", everyItem(is(false))).body("data.relatedType", everyItem(is("REQUEST")))
                .body("data[0].relatedId", is(b.requestId())).body("data[0].title", is("Nueva oferta recibida"))
                .body("data[0].body", containsString("S/ "))
                .body("meta.total", is(2)).body("meta.page", is(0)).extract();
        as(b.pyme()).get("/api/v1/notifications/unread-count").then().body("data.unread", is(2));

        String first = list.path("data[0].id");
        // Otra organización no la ve ni la marca
        as(b.carrier()).post("/api/v1/notifications/" + first + "/read").then().statusCode(404);
        as(b.carrier()).get("/api/v1/notifications/unread-count").then().body("data.unread", is(0));

        as(b.pyme()).post("/api/v1/notifications/" + first + "/read").then().statusCode(200)
                .body("data.read", is(true)).body("data.readAt", notNullValue());
        as(b.pyme()).get("/api/v1/notifications?unread=true").then().body("data", hasSize(1)).body("meta.total", is(1));
        as(b.pyme()).post("/api/v1/notifications/read-all").then().statusCode(200).body("data.updated", is(1));
        as(b.pyme()).get("/api/v1/notifications/unread-count").then().body("data.unread", is(0));

        // Aceptar: ganador BID_ACCEPTED (al envío), perdedor BID_REJECTED (a la solicitud)
        String shipmentId = as(b.pyme()).post("/api/v1/bids/" + b.bidId() + "/accept").then().statusCode(200)
                .extract().path("data.shipmentId");
        as(b.carrier()).get("/api/v1/notifications").then()
                .body("data[0].type", is("BID_ACCEPTED")).body("data[0].relatedType", is("SHIPMENT"))
                .body("data[0].relatedId", is(shipmentId));
        as(loser).get("/api/v1/notifications").then()
                .body("data[0].type", is("BID_REJECTED")).body("data[0].relatedId", is(b.requestId()));

        // Paginación y roles
        as(b.pyme()).get("/api/v1/notifications?size=1&page=1").then().body("data", hasSize(1)).body("meta.size", is(1));
        as(b.pyme()).get("/api/v1/notifications?size=500").then().statusCode(400).body("error.field", is("size"));
    }

    @Test
    void cambiosDeEstadoDelEnvioNotificanALaPyme() {
        Flows.Awarded a = Flows.delivered("Notif Envio");
        as(a.pyme()).get("/api/v1/notifications").then()
                .body("data.title", hasItems("Tu carga está en camino", "Carga entregada"));
        as(a.carrier()).get("/api/v1/notifications").then().body("data.title", hasItem("Entrega completada"));
        as(a.driver()).get("/api/v1/notifications").then().statusCode(403);
    }
}
