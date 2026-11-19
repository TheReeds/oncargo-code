package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.shipment.application.AdjustmentService;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Ola 2 zona X: evidencia del recojo y registro por la oficina (2.5) y ajustes del envío con precio final (2.6).
 */
@QuarkusTest
class ExecutionAndAdjustmentsFlowTest {

    @Inject AuthService auth;
    @Inject AdjustmentService adjustments;
    @Inject SystemTx system;
    @Inject EntityManager em;

    private Session operator() {
        var r = auth.provisionOperator("Operador X " + System.nanoTime() % 1000, Api.newRuc(), "Operador X",
                "opx" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    // ------------------------------------------------------------------ 2.5

    @Test
    void elRecojoPideLaFotoYElPesoQueExigeLaPymeYSoloElConductorSubeLaFoto() {
        Flows.Awarded a = Flows.awarded("Recojo foto");
        String pickup = a.pickupStopId();
        // Solo la PYME dueña lo pide, y antes del recojo
        as(a.carrier()).body(Map.of("photoRequired", true, "weightRequired", true))
                .put("/api/v1/shipments/" + a.shipmentId() + "/pickup-requirements").then().statusCode(403);
        as(a.pyme()).body(Map.of("photoRequired", true, "weightRequired", true))
                .put("/api/v1/shipments/" + a.shipmentId() + "/pickup-requirements").then().statusCode(200)
                .body("data.pickupPhotoRequired", is(true)).body("data.pickupWeightRequired", is(true));

        as(a.driver()).post(a.stop(pickup, "arrive")).then().statusCode(200);
        // Sin foto: TRK-009
        as(a.driver()).body(Map.of("weightKg", 2100)).post(a.stop(pickup, "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-009"));
        // La PYME y la oficina no suben la foto de la carga (RN06)
        upload(a.pyme(), a.shipmentId(), "PICKUP_PHOTO", pickup, "carga.png", PNG, "image/png").statusCode(403)
                .body("error.code", is("LOGYX-DOC-007"));
        upload(a.carrier(), a.shipmentId(), "PICKUP_PHOTO", pickup, "carga.png", PNG, "image/png").statusCode(403);
        // En la parada de entrega no vale
        upload(a.driver(), a.shipmentId(), "PICKUP_PHOTO", a.deliveryStopId(), "carga.png", PNG, "image/png")
                .statusCode(400).body("error.code", is("LOGYX-DOC-009"));
        upload(a.driver(), a.shipmentId(), "PICKUP_PHOTO", pickup, "carga.png", PNG, "image/png").statusCode(201)
                .body("data.type", is("PICKUP_PHOTO"));
        upload(a.driver(), a.shipmentId(), "REMISSION_GUIDE", pickup, "guia.pdf", PDF, "application/pdf")
                .statusCode(201);
        // Sin peso: TRK-010 en weightKg
        as(a.driver()).post(a.stop(pickup, "complete")).then().statusCode(400)
                .body("error.code", is("LOGYX-TRK-010")).body("error.field", is("weightKg"));
        as(a.driver()).body(Map.of("weightKg", 2150.5)).post(a.stop(pickup, "complete")).then().statusCode(200)
                .body("data.status", is("IN_TRANSIT"))
                .body("data.stops[0].loadedWeightKg", is(2150.5f));
        // Ya en ruta, lo que pide el recojo no cambia
        as(a.pyme()).body(Map.of("photoRequired", false, "weightRequired", false))
                .put("/api/v1/shipments/" + a.shipmentId() + "/pickup-requirements").then().statusCode(409)
                .body("error.code", is("LOGYX-SHP-008"));
    }

    @Test
    void laEntregaGuardaQuienRecibeYSoloLoVenLasPartes() {
        Flows.Awarded a = Flows.inTransit("Recibe");
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        deliveryPhoto(a.driver(), a.shipmentId(), a.deliveryStopId());
        as(a.driver()).body(Map.of("receivedByName", "Ana", "receivedByDoc", "1234"))
                .post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(400)
                .body("error.code", is("LOGYX-TRK-011")).body("error.field", is("receivedByDoc"));
        as(a.driver()).body(Map.of("receivedByName", "  Ana   Quispe ", "receivedByDoc", "4567-8901"))
                .post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(200)
                .body("data.status", is("DELIVERED"))
                .body("data.stops[1].receivedByName", is("Ana Quispe"))
                .body("data.stops[1].receivedByDoc", is("45678901"));
        as(a.pyme()).get("/api/v1/shipments/" + a.shipmentId()).then().statusCode(200)
                .body("data.stops[1].receivedByDoc", is("45678901"));
    }

    @Test
    void laOficinaRegistraUnaParadaConMotivoPeroNoSaltaLaFotoDeEntrega() {
        Flows.Awarded a = Flows.awarded("Oficina");
        String base = "/api/v1/shipments/" + a.shipmentId() + "/stops/";
        as(a.carrier()).body(Map.of("reason", " ")).post(base + a.pickupStopId() + "/office-arrive").then()
                .statusCode(400);
        // La PYME, el conductor y otro transportista no usan la vía de la oficina
        as(a.pyme()).body(Map.of("reason", "celular sin batería"))
                .post(base + a.pickupStopId() + "/office-arrive").then().statusCode(403);
        as(a.driver()).body(Map.of("reason", "celular sin batería"))
                .post(base + a.pickupStopId() + "/office-arrive").then().statusCode(403);
        Session other = register("CARRIER", "Otra flota oficina");
        as(other).body(Map.of("reason", "celular sin batería"))
                .post(base + a.pickupStopId() + "/office-arrive").then().statusCode(404);

        as(a.carrier()).body(Map.of("reason", "Celular sin batería"))
                .post(base + a.pickupStopId() + "/office-arrive").then().statusCode(200)
                .body("data.stops[0].status", is("ARRIVED"))
                .body("data.stops[0].arrivalOfficeReason", is("Celular sin batería"));
        // Idempotente
        as(a.carrier()).body(Map.of("reason", "Celular sin batería"))
                .post(base + a.pickupStopId() + "/office-arrive").then().statusCode(200);
        as(a.carrier()).body(Map.of("reason", "Celular sin batería"))
                .post(base + a.pickupStopId() + "/office-complete").then().statusCode(200)
                .body("data.status", is("IN_TRANSIT"))
                .body("data.stops[0].completionOfficeReason", is("Celular sin batería"));
        as(a.carrier()).body(Map.of("reason", "Celular sin batería"))
                .post(base + a.deliveryStopId() + "/office-arrive").then().statusCode(200);
        // RN06: la oficina no salta la foto de entrega
        as(a.carrier()).body(Map.of("reason", "Celular sin batería"))
                .post(base + a.deliveryStopId() + "/office-complete").then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-006"));
        upload(a.carrier(), a.shipmentId(), "DELIVERY_PHOTO", a.deliveryStopId(), "e.png", PNG, "image/png")
                .statusCode(403);
        // Queda auditado
        long audited = system.call(() -> ((Number) em.createNativeQuery("SELECT count(*) FROM audit_log WHERE action = "
                + "'STOP_REGISTERED_BY_OFFICE' AND entity_id = ?1").setParameter(1, UUID.fromString(a.shipmentId()))
                .getSingleResult()).longValue());
        assertEquals(3L, audited);
    }

    // ------------------------------------------------------------------ 2.6

    private String propose(Session s, String shipmentId, Map<String, Object> body) {
        return as(s).body(body).post("/api/v1/shipments/" + shipmentId + "/adjustments").then().statusCode(201)
                .body("data.status", is("PENDING")).extract().path("data.id");
    }

    @Test
    void elTransportistaProponeLaPymeDecideYElPrecioFinalSumaLosAceptados() {
        Flows.Awarded a = Flows.inTransit("Ajustes");
        String id = a.shipmentId();
        float agreed = as(a.pyme()).get("/api/v1/shipments/" + id).then().extract().path("data.agreedPrice");

        // Validaciones
        as(a.carrier()).body(Map.of("type", "TOLL", "amount", -10)).post("/api/v1/shipments/" + id + "/adjustments")
                .then().statusCode(400).body("error.code", is("LOGYX-ADJ-003")).body("error.field", is("amount"));
        as(a.carrier()).body(Map.of("type", "OTHER", "amount", 10)).post("/api/v1/shipments/" + id + "/adjustments")
                .then().statusCode(400).body("error.code", is("LOGYX-ADJ-004"));
        as(a.carrier()).body(Map.of("type", "TOLL", "amount", 10, "note", "Llama al 987654321"))
                .post("/api/v1/shipments/" + id + "/adjustments").then().statusCode(400)
                .body("error.code", is("LOGYX-NEG-001"));
        as(a.carrier()).body(Map.of("type", "TOLL", "amount", agreed + 1))
                .post("/api/v1/shipments/" + id + "/adjustments").then().statusCode(400)
                .body("error.code", is("LOGYX-ADJ-003"));
        // La PYME no propone
        as(a.pyme()).body(Map.of("type", "TOLL", "amount", 10)).post("/api/v1/shipments/" + id + "/adjustments")
                .then().statusCode(403);

        String toll = propose(a.carrier(), id, Map.of("type", "TOLL", "amount", 118, "note", "Peaje Pativilca"));
        String handling = propose(a.driver(), id, Map.of("type", "HANDLING", "amount", 59));
        String weight = propose(a.carrier(), id, Map.of("type", "WEIGHT_DIFF", "amount", -50.5));

        // El transportista no decide; la PYME rechaza solo con motivo
        as(a.carrier()).post("/api/v1/adjustments/" + toll + "/accept").then().statusCode(403);
        as(a.pyme()).body(Map.of()).post("/api/v1/adjustments/" + handling + "/reject").then().statusCode(400)
                .body("error.code", is("LOGYX-ADJ-004"));
        as(a.pyme()).body(Map.of()).post("/api/v1/adjustments/" + toll + "/accept").then().statusCode(200)
                .body("data.status", is("ACCEPTED"));
        as(a.pyme()).body(Map.of("note", "No hubo maniobra")).post("/api/v1/adjustments/" + handling + "/reject")
                .then().statusCode(200).body("data.status", is("REJECTED")).body("data.decisionNote", is("No hubo maniobra"));
        as(a.pyme()).body(Map.of()).post("/api/v1/adjustments/" + toll + "/accept").then().statusCode(409)
                .body("error.code", is("LOGYX-ADJ-007"));
        as(a.carrier()).post("/api/v1/adjustments/" + weight + "/withdraw").then().statusCode(200)
                .body("data.status", is("WITHDRAWN"));

        float expected = Math.round((agreed + 118) * 100) / 100f;
        as(a.pyme()).get("/api/v1/shipments/" + id + "/adjustments").then().statusCode(200)
                .body("data.adjustments", hasSize(3)).body("data.acceptedTotal", is(118.0f))
                .body("data.finalPrice", is(expected)).body("data.finalPriceBreakdown.amount", is(expected))
                .body("data.finalPriceBreakdown.commission", nullValue())
                .body("data.suggestions", empty());
        as(a.carrier()).get("/api/v1/shipments/" + id).then().statusCode(200)
                .body("data.adjustmentsTotal", is(118.0f)).body("data.finalPrice", is(expected))
                .body("data.pendingAdjustments", is(0))
                .body("data.finalPriceBreakdown.carrierNet", notNullValue());
        // CSV con el precio final
        String csv = new String(as(a.pyme()).get("/api/v1/shipments/export").then().statusCode(200)
                .extract().asByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Precio final (S/)"), csv.lines().findFirst().orElse(""));
        // Auditoría
        long audited = system.call(() -> ((Number) em.createNativeQuery("SELECT count(*) FROM audit_log WHERE action "
                + "LIKE 'ADJUSTMENT_%' AND entity_id = ?1").setParameter(1, UUID.fromString(id)).getSingleResult())
                .longValue());
        assertEquals(6L, audited);
    }

    @Test
    void soloLasPartesVenLosAjustesYLosEscaladosLosDecideElOperador() {
        Flows.Awarded a = Flows.inTransit("Escala");
        String id = a.shipmentId();
        String adj = as(a.carrier()).header("Idempotency-Key", "adj-" + id).body(Map.of("type", "DETENTION",
                        "amount", 80, "stopId", a.pickupStopId()))
                .post("/api/v1/shipments/" + id + "/adjustments").then().statusCode(201).extract().path("data.id");
        // Reintento con la misma clave (cola del conductor): el mismo ajuste
        as(a.carrier()).header("Idempotency-Key", "adj-" + id).body(Map.of("type", "DETENTION", "amount", 80))
                .post("/api/v1/shipments/" + id + "/adjustments").then().statusCode(201).body("data.id", is(adj));

        Session otherPyme = register("PYME", "Otra pyme ajustes");
        Session otherCarrier = register("CARRIER", "Otro carrier ajustes");
        as(otherPyme).get("/api/v1/shipments/" + id + "/adjustments").then().statusCode(404);
        as(otherCarrier).get("/api/v1/shipments/" + id + "/adjustments").then().statusCode(404);
        as(otherPyme).body(Map.of()).post("/api/v1/adjustments/" + adj + "/accept").then().statusCode(404);

        Session op = operator();
        // Antes de escalar, el operador no decide
        as(op).body(Map.of()).post("/api/v1/adjustments/" + adj + "/accept").then().statusCode(403)
                .body("error.code", is("LOGYX-ADJ-006"));
        // Vence el plazo y el job lo escala
        system.run(() -> em.createNativeQuery("UPDATE shipment_adjustments SET respond_by = now() - interval '1 minute' "
                + "WHERE id = ?1").setParameter(1, UUID.fromString(adj)).executeUpdate());
        assertTrue(adjustments.escalateDue() >= 1);
        assertEquals(0, adjustments.escalateDue(), "idempotente: ya escalado no se vuelve a escalar");
        as(op).get("/api/v1/operator/adjustments").then().statusCode(200)
                .body("data.id", hasItem(adj));
        as(a.pyme()).body(Map.of()).post("/api/v1/adjustments/" + adj + "/accept").then().statusCode(403)
                .body("error.code", is("LOGYX-ADJ-006"));
        as(op).body(Map.of("note", "Estadía con evidencia de llegada")).post("/api/v1/adjustments/" + adj + "/accept")
                .then().statusCode(200).body("data.status", is("ACCEPTED")).body("data.escalated", is(true));
        as(a.carrier()).get("/api/v1/operator/adjustments").then().statusCode(403);
    }

    @Test
    void laEstadiaSeSugiereSolaSiLaEsperaSuperaLaFranquicia() {
        Flows.Awarded a = Flows.awarded("Estadia");
        OffsetDateTime arrived = OffsetDateTime.now().minusHours(6);
        as(a.driver()).body(Map.of("occurredAt", arrived.toString())).post(a.stop(a.pickupStopId(), "arrive")).then()
                .statusCode(200);
        as(a.driver()).body(Map.of("occurredAt", arrived.plusHours(5).plusMinutes(10).toString(), "weightKg", 2500))
                .post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200);
        // 5 h 10 min − 3 h sin cargo = 2 h 10 min → 2.5 h × S/ 40 = S/ 100; 2500 kg sobre 2000 declarados → peso
        as(a.carrier()).get("/api/v1/shipments/" + a.shipmentId() + "/adjustments").then().statusCode(200)
                .body("data.suggestions.type", hasItems("DETENTION", "WEIGHT_DIFF"))
                .body("data.suggestions.find { it.type == 'DETENTION' }.amount", is(100.0f))
                .body("data.suggestions.find { it.type == 'DETENTION' }.stopId", is(a.pickupStopId()));
        as(a.pyme()).get("/api/v1/shipments/" + a.shipmentId() + "/adjustments").then().statusCode(200)
                .body("data.suggestions", empty());
        propose(a.carrier(), a.shipmentId(), Map.of("type", "DETENTION", "amount", 100, "stopId", a.pickupStopId()));
        as(a.carrier()).get("/api/v1/shipments/" + a.shipmentId() + "/adjustments").then().statusCode(200)
                .body("data.suggestions.type", not(hasItem("DETENTION")));
    }

    @Test
    void unEnvioCanceladoNoAdmiteAjustes() {
        Flows.Awarded a = Flows.awarded("Cancelado ajustes");
        as(a.pyme()).body(Map.of("reason", "Ya no va")).post("/api/v1/shipments/" + a.shipmentId() + "/cancel").then()
                .statusCode(200);
        as(a.carrier()).body(Map.of("type", "TOLL", "amount", 10))
                .post("/api/v1/shipments/" + a.shipmentId() + "/adjustments").then().statusCode(409)
                .body("error.code", is("LOGYX-ADJ-001"));
    }
}
