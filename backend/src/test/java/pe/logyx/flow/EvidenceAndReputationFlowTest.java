package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static pe.logyx.support.Api.*;

/** Evidencia de entrega y documentos (RF37, RF46–RF48) + calificaciones y Trust Score (RN06, RN09). */
@QuarkusTest
class EvidenceAndReputationFlowTest {

    @Test
    void entregaExigeFotoYLosDocumentosSoloLosVenLasPartes() {
        Flows.Awarded a = Flows.inTransit("Evidencia");
        Session stranger = register("PYME", "Evidencia Ajena SAC");
        String sid = a.shipmentId();

        // Antes de llegar a la entrega no se sube evidencia
        upload(a.driver(), sid, "DELIVERY_PHOTO", a.deliveryStopId(), "f.png", PNG, "image/png").statusCode(409)
                .body("error.code", is("LOGYX-DOC-005"));
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);

        // Completar la entrega sin foto → TRK-006
        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-006"));

        // Validaciones del archivo
        upload(a.driver(), sid, "DELIVERY_PHOTO", a.deliveryStopId(), "f.pdf", PDF, "application/pdf").statusCode(400)
                .body("error.code", is("LOGYX-DOC-001")).body("error.field", is("file"));
        upload(a.driver(), sid, "OTHER", null, "notas.txt", "hola".getBytes(), "text/plain").statusCode(400)
                .body("error.code", is("LOGYX-DOC-001"));
        // un .png que en realidad es texto tampoco pasa (se mira la firma binaria)
        upload(a.driver(), sid, "DELIVERY_PHOTO", a.deliveryStopId(), "falso.png", "no soy png".getBytes(),
                "image/png").statusCode(400).body("error.code", is("LOGYX-DOC-001"));
        byte[] big = new byte[300 * 1024];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        upload(a.driver(), sid, "DELIVERY_PHOTO", a.deliveryStopId(), "big.png", big, "image/png").statusCode(400)
                .body("error.code", is("LOGYX-DOC-002"));
        upload(a.driver(), sid, "DELIVERY_PHOTO", a.pickupStopId(), "f.png", PNG, "image/png").statusCode(400)
                .body("error.code", is("LOGYX-DOC-004")).body("error.field", is("stopId"));
        upload(a.driver(), sid, "NOPE", null, "f.png", PNG, "image/png").statusCode(400)
                .body("error.field", is("type"));
        // la PYME no sube evidencia de entrega; otra organización no sube nada
        upload(a.pyme(), sid, "DELIVERY_PHOTO", a.deliveryStopId(), "f.png", PNG, "image/png").statusCode(403);
        upload(stranger, sid, "REMISSION_GUIDE", null, "g.pdf", PDF, "application/pdf").statusCode(404);

        String photo = upload(a.driver(), sid, "DELIVERY_PHOTO", a.deliveryStopId(), "../../etc/foto entrega.jpg",
                JPEG, "image/jpeg").statusCode(201)
                .body("data.type", is("DELIVERY_PHOTO")).body("data.contentType", is("image/jpeg"))
                .body("data.fileName", is("foto entrega.jpg")).body("data.stopId", is(a.deliveryStopId()))
                .extract().path("data.id");
        upload(a.driver(), sid, "SIGNATURE", a.deliveryStopId(), "firma.png", PNG, "image/png").statusCode(201);
        upload(a.pyme(), sid, "REMISSION_GUIDE", null, "guia.pdf", PDF, "application/pdf").statusCode(201)
                .body("data.contentType", is("application/pdf"));

        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(200)
                .body("data.status", is("DELIVERED"));

        // Listado y descarga: partes sí, terceros no
        as(a.pyme()).get("/api/v1/shipments/" + sid + "/documents").then().statusCode(200)
                .body("data", hasSize(3)).body("data.type", contains("DELIVERY_PHOTO", "SIGNATURE", "REMISSION_GUIDE"))
                .body("data[0].downloadUrl", is("/api/v1/documents/" + photo + "/file"));
        byte[] bytes = raw(a.pyme()).get("/api/v1/documents/" + photo + "/file").then().statusCode(200)
                .contentType("image/jpeg").header("X-Content-Type-Options", "nosniff")
                .extract().asByteArray();
        assertArrayEquals(JPEG, bytes);
        raw(stranger).get("/api/v1/documents/" + photo + "/file").then().statusCode(404);
        as(stranger).get("/api/v1/shipments/" + sid + "/documents").then().statusCode(404);
        as(a.pyme()).get("/api/v1/documents?type=REMISSION_GUIDE").then().statusCode(200)
                .body("data", hasSize(1)).body("meta.total", is(1));
        as(a.carrier()).get("/api/v1/documents").then().statusCode(200).body("meta.total", is(3));
    }

    @Test
    void calificacionesEnAmbasDireccionesYTrustScoreBayesiano() {
        Flows.Awarded pending = Flows.inTransit("Rep Pendiente");
        // RN06: no entregado → REV-001 (y el GET lo explica)
        as(pending.pyme()).body(Map.of("punctuality", 5, "cargoCondition", 5, "communication", 5))
                .post("/api/v1/shipments/" + pending.shipmentId() + "/reviews").then().statusCode(409)
                .body("error.code", is("LOGYX-REV-001"));
        as(pending.pyme()).get("/api/v1/shipments/" + pending.shipmentId() + "/reviews").then().statusCode(200)
                .body("data.canReview", is(false)).body("data.blockedCode", is("LOGYX-REV-001"));

        Flows.Awarded a = Flows.delivered("Reputacion");
        String reviews = "/api/v1/shipments/" + a.shipmentId() + "/reviews";
        Session stranger = register("PYME", "Rep Ajena SAC");

        // Sin calificaciones el Trust Score es el prior (75)
        as(stranger).get("/api/v1/organizations/" + a.carrier().orgId() + "/reputation").then().statusCode(200)
                .body("data.trustScore", is(75.0f)).body("data.totalReviews", is(0))
                .body("data.averageRating", nullValue()).body("data.priorMean", is(75));

        as(a.pyme()).get(reviews).then().body("data.canReview", is(true)).body("data.blockedCode", nullValue());
        as(a.pyme()).body(Map.of("punctuality", 6, "cargoCondition", 5, "communication", 5)).post(reviews).then()
                .statusCode(400).body("error.field", is("punctuality"));
        as(stranger).body(Map.of("punctuality", 5, "cargoCondition", 5, "communication", 5)).post(reviews).then()
                .statusCode(404);
        as(a.driver()).body(Map.of("punctuality", 5, "cargoCondition", 5, "communication", 5)).post(reviews).then()
                .statusCode(403);

        // PYME → transportista: (5+4+5)/3 = 4.67 → x = 91.75 ; (5·75 + 91.75)/6 = 77.8
        as(a.pyme()).body(Map.of("punctuality", 5, "cargoCondition", 4, "communication", 5,
                        "comment", "Puntual y cuidadoso")).post(reviews).then().statusCode(201)
                .body("data.direction", is("PYME_TO_CARRIER")).body("data.rating", is(4.67f))
                .body("data.reviewedOrgId", is(a.carrier().orgId()));
        as(a.pyme()).body(Map.of("punctuality", 5, "cargoCondition", 5, "communication", 5)).post(reviews).then()
                .statusCode(409).body("error.code", is("LOGYX-REV-003"));

        // Transportista → PYME: 1/1/1 → x = 0 ; (375 + 0)/6 = 62.5
        as(a.carrier()).body(Map.of("punctuality", 1, "cargoCondition", 1, "communication", 1)).post(reviews).then()
                .statusCode(201).body("data.direction", is("CARRIER_TO_PYME"));

        as(stranger).get("/api/v1/organizations/" + a.carrier().orgId() + "/reputation").then().statusCode(200)
                .body("data.trustScore", is(77.8f)).body("data.totalReviews", is(1))
                .body("data.averageRating", is(4.67f)).body("data.averageCargoCondition", is(4.0f))
                .body("data.recentReviews[0].comment", is("Puntual y cuidadoso"))
                .body("data.recentReviews[0].reviewerName", is("Pyme Reputacion"));
        as(stranger).get("/api/v1/organizations/" + a.pyme().orgId() + "/reputation").then()
                .body("data.trustScore", is(62.5f));
        as(stranger).get("/api/v1/organizations/" + a.carrier().orgId() + "/reviews?size=1").then().statusCode(200)
                .body("data", hasSize(1)).body("meta.total", is(1));
        as(a.pyme()).get(reviews).then().body("data.canReview", is(false))
                .body("data.blockedCode", is("LOGYX-REV-003")).body("data.reviews", hasSize(2));

        // El Trust Score aparece en las ofertas y solicitudes (aditivo)
        String vehicle = vehicle(a.carrier(), plate(), 8000);
        var req = publish(a.pyme(), 1000).statusCode(201).body("data.requesterTrustScore", is(62.5f)).extract();
        String rid = req.path("data.id");
        float price = req.path("data.suggestedPrice");
        as(a.carrier()).body(Map.of("vehicleId", vehicle, "amount", price))
                .post("/api/v1/shipment-requests/" + rid + "/bids").then().statusCode(201)
                .body("data.carrierTrustScore", is(77.8f)).body("data.assignedByOperator", is(false));

        // Notificación de nueva calificación
        as(a.carrier()).get("/api/v1/notifications").then()
                .body("data.type", hasItems("NEW_REVIEW", "BID_ACCEPTED", "SHIPMENT_STATUS_CHANGE"));
        as(a.pyme()).get("/api/v1/notifications").then()
                .body("data.type", hasItems("NEW_REVIEW", "NEW_BID", "SHIPMENT_STATUS_CHANGE"));
    }
}
