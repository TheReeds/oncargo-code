package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Ítem 1.13: búsqueda por texto del operador y lectura (auditada) de ofertas y chat de una solicitud. */
@QuarkusTest
class OperatorSearchAndChatFlowTest {

    @Inject AuthService auth;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Búsqueda " + System.nanoTime() % 1000, Api.newRuc(), "Operador Búsqueda",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    private static String thread(String requestId, String carrierOrgId) {
        return "/api/v1/shipment-requests/" + requestId + "/threads/" + carrierOrgId + "/messages";
    }

    @Test
    void buscaSolicitudesEnviosYOrganizacionesPorTexto() {
        String tag = "Zafiro" + System.nanoTime() % 100000;
        Flows.Awarded a = Flows.awarded(tag);
        Session op = operator();
        String plate = as(op).get("/api/v1/operator/shipments?q=" + tag).then().statusCode(200)
                .body("meta.total", is(1)).body("data[0].id", is(a.shipmentId()))
                .extract().path("data[0].vehiclePlate");

        // Solicitudes: por razón social del solicitante, del transportista adjudicado, RUC y placa
        as(op).get("/api/v1/operator/requests?q=Pyme " + tag).then().statusCode(200)
                .body("meta.total", is(1)).body("data[0].id", is(a.requestId()));
        as(op).get("/api/v1/operator/requests?q=Carrier " + tag).then().statusCode(200)
                .body("data.id", hasItem(a.requestId()));
        as(op).get("/api/v1/operator/requests?q=" + plate).then().statusCode(200)
                .body("data.id", hasItem(a.requestId()));
        // la búsqueda convive con los demás filtros
        as(op).get("/api/v1/operator/requests?q=" + tag + "&status=CANCELLED").then().statusCode(200)
                .body("meta.total", is(0));
        as(op).get("/api/v1/operator/requests?q=" + tag + "&status=MATCHED").then().statusCode(200)
                .body("meta.total", is(1));
        // Envíos: por placa y por prefijo del id
        as(op).get("/api/v1/operator/shipments?q=" + plate).then().statusCode(200)
                .body("data.id", hasItem(a.shipmentId()));
        as(op).get("/api/v1/operator/shipments?q=" + a.shipmentId().substring(0, 8)).then().statusCode(200)
                .body("data.id", hasItem(a.shipmentId()));
        // Organizaciones: por razón social y por el correo de un usuario; las operadoras no aparecen
        as(op).get("/api/v1/operator/organizations?status=ALL&q=Carrier " + tag).then().statusCode(200)
                .body("meta.total", is(1)).body("data[0].id", is(a.carrier().orgId()));
        as(op).get("/api/v1/operator/organizations?status=ALL&q=" + a.pyme().email()).then().statusCode(200)
                .body("data.id", hasItem(a.pyme().orgId()));
        as(op).get("/api/v1/operator/organizations?status=ALL&q=LOGYX Búsqueda").then().statusCode(200)
                .body("meta.total", is(0));
        // Sin coincidencias → vacío; sin q → el listado normal
        as(op).get("/api/v1/operator/requests?q=NoExisteEsteTexto" + System.nanoTime()).then().statusCode(200)
                .body("meta.total", is(0)).body("data", hasSize(0));
        as(op).get("/api/v1/operator/requests").then().statusCode(200).body("meta.total", greaterThanOrEqualTo(1));
    }

    @Test
    void laBusquedaValidaElTextoYSoloLaUsaElOperador() {
        Session op = operator();
        as(op).get("/api/v1/operator/requests?q=a").then().statusCode(400).body("error.field", is("q"));
        as(op).get("/api/v1/operator/shipments?q=" + "x".repeat(61)).then().statusCode(400).body("error.field", is("q"));
        // los comodines de LIKE no se interpretan
        as(op).get("/api/v1/operator/requests?q=%25%25").then().statusCode(200);
        Session pyme = register("PYME", "Busca Intrusa");
        as(pyme).get("/api/v1/operator/requests?q=algo").then().statusCode(403);
    }

    @Test
    void ofertasYChatDeUnaSolicitudEnSoloLecturaQuedanAuditados() {
        Flows.Bidding b = Flows.bidding("Chat" + System.nanoTime() % 100000);
        String path = thread(b.requestId(), b.carrier().orgId());
        as(b.carrier()).body(Map.of("body", "¿Cuándo recojo?")).post(path).then().statusCode(201);
        as(b.pyme()).body(Map.of("body", "El jueves por la mañana")).post(path).then().statusCode(201);
        Session op = operator();

        as(op).get("/api/v1/operator/requests/" + b.requestId() + "/bids").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].id", is(b.bidId()))
                .body("data[0].carrierName", startsWith("Carrier")).body("data[0].status", is("PENDING"));

        as(op).get("/api/v1/operator/requests/" + b.requestId() + "/threads?reason=Disputa del cliente").then()
                .statusCode(200).body("data", hasSize(1)).body("data[0].carrierOrgId", is(b.carrier().orgId()))
                .body("data[0].messageCount", is(2)).body("data[0].bidId", is(b.bidId()));

        as(op).get("/api/v1/operator/requests/" + b.requestId() + "/threads/" + b.carrier().orgId() + "/messages")
                .then().statusCode(200).body("data.body", contains("¿Cuándo recojo?", "El jueves por la mañana"))
                .body("data[0].senderName", startsWith("Carrier")).body("data[0].system", is(false));

        // Auditoría: una fila por consulta (hilos y mensajes), con el motivo, sobre esa solicitud
        as(op).get("/api/v1/operator/audit?action=OPERATOR_VIEWED_CHAT&entityId=" + b.requestId()).then().statusCode(200)
                .body("meta.total", is(2)).body("data.entity", everyItem(is("shipment_request")))
                .body("data.detail", hasItem(containsString("Disputa del cliente")))
                .body("data.actorEmail", everyItem(is(op.email())));
        // Pedir páginas anteriores no vuelve a auditar
        as(op).get("/api/v1/operator/requests/" + b.requestId() + "/threads/" + b.carrier().orgId()
                + "/messages?before=" + java.util.UUID.randomUUID()).then().statusCode(400);
        as(op).get("/api/v1/operator/audit?action=OPERATOR_VIEWED_CHAT&entityId=" + b.requestId()).then()
                .body("meta.total", is(2));

        // Solo lectura y solo operador: ni la PYME ni el transportista usan estas rutas; el operador no escribe
        as(b.pyme()).get("/api/v1/operator/requests/" + b.requestId() + "/threads").then().statusCode(403);
        as(b.carrier()).get("/api/v1/operator/requests/" + b.requestId() + "/bids").then().statusCode(403);
        as(op).body(Map.of("body", "hola")).post(thread(b.requestId(), b.carrier().orgId())).then().statusCode(403);
        as(op).get("/api/v1/operator/requests/" + java.util.UUID.randomUUID() + "/bids").then().statusCode(404);
        as(op).get("/api/v1/operator/requests/" + b.requestId() + "/threads?reason=" + "x".repeat(201)).then()
                .statusCode(400).body("error.field", is("reason"));
    }
}
