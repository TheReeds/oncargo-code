package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Incidencias (RF52–RF55, RN11) y panel del operador (RF56–RF60, RN10). */
@QuarkusTest
class IncidentAndOperatorFlowTest {

    @Inject AuthService auth;
    @Inject EntityManager em;
    @Inject pe.logyx.shared.tenancy.SystemTx systemTx;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Operaciones " + System.nanoTime() % 1000, Api.newRuc(), "Operador Test",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    private void backdate(String requestId, int hours) {
        systemTx.run(() -> em.createNativeQuery(
                        "UPDATE shipment_requests SET created_at = now() - make_interval(hours => ?1) WHERE id = ?2")
                .setParameter(1, hours).setParameter(2, UUID.fromString(requestId)).executeUpdate());
    }

    @Test
    void incidenciaActivaCongelaElEnvioHastaQueElOperadorLaResuelve() {
        Session op = operator();
        Flows.Awarded a = Flows.inTransit("Incidencia");
        Session stranger = register("CARRIER", "Incidencia Ajena");
        String sid = a.shipmentId();

        // La PYME reporta (RF52)
        String incidentId = as(a.pyme()).body(Map.of("category", "CARGO_DAMAGE", "description", "Lleva 6 h de retraso"))
                .post("/api/v1/shipments/" + sid + "/incidents").then().statusCode(201)
                .body("data.status", is("OPEN")).body("data.active", is(true))
                .body("data.reportedByOrgId", is(a.pyme().orgId()))
                .extract().path("data.id");
        as(a.carrier()).body(Map.of("category", "OTHER", "description", "Otra"))
                .post("/api/v1/shipments/" + sid + "/incidents").then().statusCode(409)
                .body("error.code", is("LOGYX-INC-001"));
        as(stranger).body(Map.of("category", "OTHER", "description", "x"))
                .post("/api/v1/shipments/" + sid + "/incidents").then().statusCode(404);

        // RN11: el conductor no avanza; el envío lo indica
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-005"));
        as(a.pyme()).get("/api/v1/shipments/" + sid).then().body("data.hasActiveIncident", is(true));
        as(a.driver()).get("/api/v1/driver/shipments/active").then().body("data.hasActiveIncident", is(true));

        // Hilo de comentarios: partes y operador; terceros no
        as(a.driver()).body(Map.of("body", "Hubo un derrumbe en la vía")).post("/api/v1/incidents/" + incidentId + "/comments")
                .then().statusCode(201).body("data.comments", hasSize(1))
                .body("data.comments[0].authorOrgId", is(a.carrier().orgId()));
        as(op).body(Map.of("body", "Revisando con el transportista")).post("/api/v1/incidents/" + incidentId + "/comments")
                .then().statusCode(201).body("data.comments", hasSize(2));
        as(stranger).get("/api/v1/incidents/" + incidentId).then().statusCode(404);
        as(stranger).body(Map.of("body", "x")).post("/api/v1/incidents/" + incidentId + "/comments").then().statusCode(404);

        // Notificaciones: la otra parte y el operador
        as(a.carrier()).get("/api/v1/notifications").then().body("data.type", hasItem("INCIDENT_REPORTED"));
        as(op).get("/api/v1/notifications").then()
                .body("data.find { it.relatedId == '" + incidentId + "' }.type", is("INCIDENT_REPORTED"));

        // Listados
        as(a.pyme()).get("/api/v1/incidents?active=true").then().statusCode(200).body("data.id", contains(incidentId));
        as(stranger).get("/api/v1/incidents").then().statusCode(200).body("data", empty());
        as(op).get("/api/v1/incidents?status=OPEN,IN_REVIEW").then().statusCode(200).body("data.id", hasItem(incidentId));
        as(op).get("/api/v1/incidents?status=NOPE").then().statusCode(400).body("error.field", is("status"));
        as(op).get("/api/v1/operator/sla-alerts").then().statusCode(200)
                .body("data.shipmentsWithIncidents.incidentId", hasItem(incidentId));

        // Solo el operador cambia el estado; RESOLVED exige notas; transiciones válidas
        as(a.pyme()).body(Map.of("status", "RESOLVED", "resolutionNotes", "x"))
                .post("/api/v1/incidents/" + incidentId + "/status").then().statusCode(403);
        as(op).body(Map.of("status", "IN_REVIEW")).post("/api/v1/incidents/" + incidentId + "/status").then()
                .statusCode(200).body("data.status", is("IN_REVIEW")).body("data.active", is(true));
        as(op).body(Map.of("status", "RESOLVED")).post("/api/v1/incidents/" + incidentId + "/status").then()
                .statusCode(400).body("error.code", is("LOGYX-INC-003")).body("error.field", is("resolutionNotes"));
        // RF55: sin estado final del envío, o con uno que no corresponde (pendiente no se "entrega"), no se resuelve
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "Ruta alterna"))
                .post("/api/v1/incidents/" + incidentId + "/status").then().statusCode(400)
                .body("error.code", is("LOGYX-INC-006")).body("error.field", is("shipmentOutcome"));
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "Ruta alterna", "shipmentOutcome", "RESUME_X"))
                .post("/api/v1/incidents/" + incidentId + "/status").then().statusCode(400);
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "Ruta alterna por Juliaca; nueva ETA 18:00",
                        "shipmentOutcome", "RESUME"))
                .post("/api/v1/incidents/" + incidentId + "/status").then().statusCode(200)
                .body("data.status", is("RESOLVED")).body("data.active", is(false))
                .body("data.shipmentOutcome", is("RESUME"))
                .body("data.resolvedAt", notNullValue());
        as(op).body(Map.of("status", "OPEN")).post("/api/v1/incidents/" + incidentId + "/status").then()
                .statusCode(409).body("error.code", is("LOGYX-INC-002"));

        // Liberado: el conductor continúa
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200)
                .body("data.hasActiveIncident", is(false));
        as(a.pyme()).get("/api/v1/notifications").then().body("data.type", hasItem("INCIDENT_RESOLVED"));

        // Cerrada: no admite comentarios
        as(op).body(Map.of("status", "CLOSED")).post("/api/v1/incidents/" + incidentId + "/status").then().statusCode(200);
        as(a.pyme()).body(Map.of("body", "Gracias")).post("/api/v1/incidents/" + incidentId + "/comments").then()
                .statusCode(409).body("error.code", is("LOGYX-INC-004"));
    }

    @Test
    void operadorAsignaManualmenteTrasElSlaYVeMetricas() {
        Session op = operator();
        Session pyme = register("PYME", "SLA Pyme SAC");
        Session carrier = register("CARRIER", "SLA Carrier SRL");
        Session other = register("CARRIER", "SLA Otro SRL");
        String vehicle = vehicle(carrier, plate(), 9000);
        String small = vehicle(carrier, plate(), 500);
        String otherVehicle = vehicle(other, plate(), 9000);
        var req = publish(pyme, 3000).statusCode(201).extract();
        String rid = req.path("data.id");
        float floor = req.path("data.priceFloor");

        // Solo el operador; antes del SLA → OPS-001
        as(pyme).get("/api/v1/operator/sla-alerts").then().statusCode(403);
        as(carrier).get("/api/v1/operator/metrics").then().statusCode(403);
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", vehicle))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(409)
                .body("error.code", is("LOGYX-OPS-001"));

        backdate(rid, 25);
        as(op).get("/api/v1/operator/sla-alerts").then().statusCode(200)
                .body("data.slaHours", is(24))
                .body("data.requestsWithoutBids.id", hasItem(rid))
                .body("data.requestsWithoutBids.find { it.id == '" + rid + "' }.hoursWithoutBids", greaterThanOrEqualTo(25));
        as(op).get("/api/v1/operator/requests/" + rid + "/candidates").then().statusCode(200)
                .body("data.vehicleId", hasItems(vehicle, otherVehicle)).body("data.vehicleId", not(hasItem(small)));
        as(op).get("/api/v1/shipment-requests/" + rid).then().statusCode(200);

        // Validaciones: transportista y vehículo, capacidad (RN02), piso (RN03)
        as(op).body(Map.of("carrierOrgId", pyme.orgId(), "vehicleId", vehicle))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(400)
                .body("error.code", is("LOGYX-OPS-002")).body("error.field", is("carrierOrgId"));
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", otherVehicle))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(404);
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", small))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(400)
                .body("error.code", is("LOGYX-CAP-001"));
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", vehicle, "amount", floor - 1))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(400)
                .body("error.code", is("LOGYX-BID-001"));

        String shipmentId = as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", vehicle))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(200)
                .body("data.assignedByOperator", is(true)).body("data.bidStatus", is("ACCEPTED"))
                .body("data.amount", is((float) req.path("data.suggestedPrice")))
                .extract().path("data.shipmentId");

        // Las partes ven el resultado como si se hubiera aceptado una oferta
        as(pyme).get("/api/v1/shipment-requests/" + rid).then().body("data.status", is("MATCHED"));
        as(pyme).get("/api/v1/shipment-requests/" + rid + "/bids").then()
                .body("data[0].assignedByOperator", is(true)).body("data[0].carrierOrgId", is(carrier.orgId()));
        as(carrier).get("/api/v1/shipments/" + shipmentId).then().statusCode(200).body("data.status", is("PENDING"));
        as(carrier).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'BID_ACCEPTED' }.title", is("LOGYX te asignó una carga"));
        as(pyme).get("/api/v1/notifications").then()
                .body("data.find { it.relatedId == '" + shipmentId + "' }.title", is("Transportista asignado"));
        // Ya no se puede reasignar
        as(op).body(Map.of("carrierOrgId", other.orgId(), "vehicleId", otherVehicle))
                .post("/api/v1/operator/requests/" + rid + "/assign").then().statusCode(409)
                .body("error.code", is("LOGYX-OPS-001"));

        as(op).get("/api/v1/operator/metrics").then().statusCode(200)
                .body("data.matchedRequests", greaterThanOrEqualTo(1)).body("data.shipmentsPending", greaterThanOrEqualTo(1))
                .body("data.carriers", greaterThanOrEqualTo(2)).body("data.pymes", greaterThanOrEqualTo(1));
    }

    @Test
    void operadorNoSeRegistraPublicamente() {
        Map<String, Object> body = Map.of("organizationType", "OPERATOR", "legalName", "Falso Operador",
                "ruc", Api.newRuc(), "fullName", "X", "email", "falso" + System.nanoTime() + "@test.pe",
                "password", "Secreta123", "acceptPrivacyPolicy", true, "acceptTerms", true);
        io.restassured.RestAssured.given().contentType("application/json").body(body)
                .post("/api/v1/auth/register").then().statusCode(403);
    }
}
