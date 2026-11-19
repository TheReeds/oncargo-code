package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** P2/P3 de la revisión de flujos cerrados con la V15: F22, F29, F42, F44, F45, F46, F49, F59. */
@QuarkusTest
class FlowGapsP2P3FlowTest {

    @Inject AuthService auth;
    @Inject SystemTx systemTx;
    @Inject EntityManager em;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX P2P3 " + System.nanoTime() % 1000, Api.newRuc(), "Operador Test", email,
                "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    @Test
    void f42_el404ConcuerdaEnGeneroConLaEntidad() {
        Session pyme = register("PYME", "F42 Pyme");
        Session carrier = register("CARRIER", "F42 Carrier");
        Session op = operator();
        String nope = UUID.randomUUID().toString();
        as(pyme).get("/api/v1/shipment-requests/" + nope).then().statusCode(404)
                .body("error.code", is("LOGYX-GEN-404")).body("error.message", is("Solicitud no encontrada"));
        as(pyme).get("/api/v1/shipments/" + nope).then().statusCode(404).body("error.message", is("Envío no encontrado"));
        as(pyme).get("/api/v1/incidents/" + nope).then().statusCode(404)
                .body("error.message", is("Incidencia no encontrada"));
        as(carrier).post("/api/v1/bids/" + nope + "/withdraw").then().statusCode(404)
                .body("error.message", is("Oferta no encontrada"));
        as(op).get("/api/v1/operator/requests/" + nope + "/candidates").then().statusCode(404)
                .body("error.message", is("Solicitud no encontrada"));
    }

    @Test
    void f44_rutaInexistenteEnEspanolYConCodigoEstable() {
        Session pyme = register("PYME", "F44 Pyme");
        as(pyme).post("/api/v1/trip-bookings/x/confirmar").then().statusCode(404)
                .body("error.code", is("LOGYX-HTTP-404")).body("error.message", is("No existe el recurso solicitado"))
                .body("error.message", not(containsString("Unable")));
        as(pyme).delete("/api/v1/vehicles").then().statusCode(anyOf(is(404), is(405)))
                .body("error.code", startsWith("LOGYX-HTTP-")).body("error.message", not(containsString("HTTP")));
    }

    @Test
    void f45_descargaInexistenteDevuelveJsonYLas413TambienTienenCuerpo() {
        Flows.Awarded a = Flows.awarded("F45");
        // Accept: */* en un endpoint que produce image/pdf: antes el cuerpo era el toString() de Java
        var body = as(a.pyme()).header("Accept", "*/*").get("/api/v1/documents/" + UUID.randomUUID() + "/file").then()
                .statusCode(404).contentType(containsString("application/json"))
                .body("error.code", is("LOGYX-GEN-404")).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("ApiError["), body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("no encontrad"), body);
        // Más que el límite HTTP (12 MB): 413 con el mismo sobre, en español, campo "file". Cliente con Content-Length
        // conocido, como un navegador con FormData.
        byte[] big = new byte[13 * 1024 * 1024];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        String boundary = "----logyx" + System.nanoTime();
        var out = new java.io.ByteArrayOutputStream();
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\nOTHER\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"enorme.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes());
        out.writeBytes(big);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes());
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"
                        + io.restassured.RestAssured.port + "/api/v1/shipments/" + a.shipmentId() + "/documents"))
                .header("Authorization", "Bearer " + a.carrier().token())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        java.net.http.HttpResponse<String> response;
        try {
            response = java.net.http.HttpClient.newHttpClient().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        org.junit.jupiter.api.Assertions.assertEquals(413, response.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.headers().firstValue("content-type").orElse("")
                .contains("application/json"), response.headers().toString());
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("\"LOGYX-HTTP-413\"")
                && response.body().contains("\"field\":\"file\"") && response.body().contains("demasiado grande"),
                response.body());
    }

    @Test
    void f46_notificacionDeIncidenciaResueltaPuntuadaYSinPuntoDoble() {
        Flows.Awarded a = Flows.inTransit("F46");
        Session op = operator();
        String incident = as(a.pyme()).body(Map.of("category", "DELAY", "description", "Retraso por bloqueo"))
                .post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(201).extract().path("data.id");
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "QA: se coordinó, continúa.",
                        "shipmentOutcome", "RESUME"))
                .post("/api/v1/incidents/" + incident + "/status").then().statusCode(200);
        as(a.pyme()).get("/api/v1/notifications").then().statusCode(200)
                .body("data.find { it.type == 'INCIDENT_RESOLVED' }.body",
                        is("Resolución del operador: «QA: se coordinó, continúa». El envío puede continuar."));
    }

    @Test
    void f49_mensajesDeParadaDistinguenLaCausa() {
        Flows.Awarded a = Flows.awarded("F49");
        // Completar sin haber llegado: TRK-003 (antes TRK-001 o TRK-006)
        as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-003"));
        as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200);
        // Parada ya completada: desde la Ola 1 (roadmap 1.11) repetir es idempotente y responde 200 con el envío
        // (antes TRK-007). Ver ShipmentTrackingFlowTest.
        as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200)
                .body("data.stops[0].status", is("COMPLETED"));
        as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        // Entrega: sin llegada TRK-003 aunque falte la foto; con llegada y sin foto TRK-006
        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-003"));
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-006"));
        // Una parada que no es la siguiente sigue siendo TRK-001
        Flows.Awarded b = Flows.awarded("F49 orden");
        as(b.driver()).post(b.stop(b.deliveryStopId(), "arrive")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-001"));
    }

    @Test
    void f22_historialDelConductorTrasEntregar() {
        Flows.Awarded done = Flows.delivered("F22");
        as(done.driver()).get("/api/v1/driver/shipments").then().statusCode(200).body("data", empty());
        as(done.driver()).get("/api/v1/driver/shipments?scope=ACTIVE").then().body("data", empty());
        as(done.driver()).get("/api/v1/driver/shipments?scope=history").then().statusCode(200)
                .body("data.id", contains(done.shipmentId())).body("data[0].status", is("DELIVERED"));
        as(done.driver()).get("/api/v1/driver/shipments?scope=ALL").then().statusCode(200)
                .body("data.id", contains(done.shipmentId()));
        as(done.driver()).get("/api/v1/driver/shipments?scope=zzz").then().statusCode(400)
                .body("error.code", is("LOGYX-VAL-001")).body("error.field", is("scope"));
        // El historial es solo del propio conductor
        Flows.Awarded other = Flows.awarded("F22 otro");
        as(other.driver()).get("/api/v1/driver/shipments?scope=ALL").then()
                .body("data.id", contains(other.shipmentId())).body("data.id", not(hasItem(done.shipmentId())));
    }

    @Test
    void f29_candidatosSoloConSlaVencidoYRespetanLaMatrizDeCarga() {
        Session op = operator();
        Session pyme = register("PYME", "F29 Pyme");
        Session carrier = register("CARRIER", "F29 Carrier");
        String large = vehicleOf(carrier, "TRUCK_LARGE", 12000, null);
        String platform = vehicleOf(carrier, "PLATFORM", 12000, null);
        String rid = publishCargo(pyme, "HAZARDOUS", 9000, null, 2).statusCode(201).extract().path("data.id");

        // Antes del SLA: 409 igual que asignar
        as(op).get("/api/v1/operator/requests/" + rid + "/candidates").then().statusCode(409)
                .body("error.code", is("LOGYX-OPS-001"));
        systemTx.run(() -> em.createNativeQuery(
                        "UPDATE shipment_requests SET created_at = now() - make_interval(hours => 25) WHERE id = ?1")
                .setParameter(1, UUID.fromString(rid)).executeUpdate());
        // Matriz RN02: la carga peligrosa no va en plataforma
        as(op).get("/api/v1/operator/requests/" + rid + "/candidates").then().statusCode(200)
                .body("data.vehicleId", hasItem(large)).body("data.vehicleId", not(hasItem(platform)));
        // Adjudicada: ya no hay candidatos que mostrar
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", large))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(200);
        as(op).get("/api/v1/operator/requests/" + rid + "/candidates").then().statusCode(409)
                .body("error.code", is("LOGYX-OPS-001"));
    }

    @Test
    void f59_metricasDelOperadorTraenElValorTransaccionado() {
        Session op = operator();
        Flows.Awarded a = Flows.delivered("F59");
        var before = as(op).get("/api/v1/operator/metrics").then().statusCode(200)
                .body("data.gmv", notNullValue()).body("data.gmvDelivered", notNullValue()).extract();
        float gmv = ((Number) before.path("data.gmv")).floatValue();
        float delivered = ((Number) before.path("data.gmvDelivered")).floatValue();
        org.junit.jupiter.api.Assertions.assertTrue(gmv >= delivered && delivered > 0, gmv + " / " + delivered);
        float price = as(a.pyme()).get("/api/v1/shipments/" + a.shipmentId()).then().extract().path("data.agreedPrice");
        org.junit.jupiter.api.Assertions.assertTrue(delivered >= price);
    }
}
