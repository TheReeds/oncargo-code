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
import pe.logyx.support.Mailbox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static pe.logyx.support.Api.*;

/** Ola 2 zona X: incidencias v2 (2.7) y operador 360, señales y cuentas (2.15). */
@QuarkusTest
class IncidentsV2AndOperator360FlowTest {

    @Inject AuthService auth;
    @Inject SystemTx system;
    @Inject EntityManager em;

    private record Op(Session session, String profileId) {}

    private Op operator() {
        String email = "op360" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("Operador 360 " + System.nanoTime() % 1000, Api.newRuc(), "Operador 360",
                email, "Operador123");
        return new Op(new Session(r.accessToken(), r.user().organization().id().toString(), email),
                r.user().profile().id().toString());
    }

    private static String incident(Session s, String shipmentId, Map<String, Object> body) {
        return as(s).body(body).post("/api/v1/shipments/" + shipmentId + "/incidents").then().statusCode(201)
                .extract().path("data.id");
    }

    // ------------------------------------------------------------------ 2.7

    @Test
    void unaInformativaNoCongelaYQuienLaReportoLaRetira() {
        Flows.Awarded a = Flows.inTransit("Informativa");
        String delay = incident(a.driver(), a.shipmentId(), Map.of("category", "ROAD_BLOCKED",
                "description", "Huelga en la carretera, vamos lento"));
        as(a.pyme()).get("/api/v1/incidents/" + delay).then().statusCode(200)
                .body("data.blocking", is(false)).body("data.holdsShipment", is(false))
                .body("data.severity", is("MEDIUM")).body("data.dueAt", notNullValue())
                .body("data.canWithdraw", is(false));
        // El viaje sigue: la llegada a la entrega no se bloquea (RN11 solo con las que congelan)
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        // Una del mismo tipo, no; una que congela, sí
        as(a.carrier()).body(Map.of("category", "ROAD_BLOCKED", "description", "Otra vía"))
                .post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(409)
                .body("error.code", is("LOGYX-INC-001"));
        // Solo quien la reportó la retira
        as(a.pyme()).post("/api/v1/incidents/" + delay + "/withdraw").then().statusCode(403)
                .body("error.code", is("LOGYX-INC-007"));
        as(a.carrier()).post("/api/v1/incidents/" + delay + "/withdraw").then().statusCode(200)
                .body("data.status", is("CLOSED")).body("data.withdrawnAt", notNullValue());

        String theft = incident(a.pyme(), a.shipmentId(), Map.of("category", "THEFT", "description",
                "Reportan robo parcial", "claimedAmount", 1500));
        as(a.pyme()).get("/api/v1/incidents/" + theft).then().statusCode(200)
                .body("data.blocking", is(true)).body("data.severity", is("CRITICAL"))
                .body("data.claimedAmount", is(1500.0f));
        deliveryPhoto(a.driver(), a.shipmentId(), a.deliveryStopId());
        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-005"));
        as(a.pyme()).post("/api/v1/incidents/" + theft + "/withdraw").then().statusCode(409)
                .body("error.code", is("LOGYX-INC-007"));
    }

    @Test
    void emergenciaConUbicacionAdjuntosNotasInternasAsignacionYReapertura() {
        Flows.Awarded a = Flows.inTransit("Emergencia");
        String photo = upload(a.driver(), a.shipmentId(), "OTHER", null, "choque.png", PNG, "image/png")
                .statusCode(201).extract().path("data.id");
        Flows.Awarded other = Flows.awarded("Emergencia ajena");
        String foreign = upload(other.pyme(), other.shipmentId(), "OTHER", null, "x.png", PNG, "image/png")
                .statusCode(201).extract().path("data.id");
        Map<String, Object> body = new java.util.HashMap<>(Map.of("category", "ACCIDENT", "description",
                "Choque leve en el km 120", "latitude", -15.5, "longitude", -70.13,
                "occurredAt", OffsetDateTime.now().minusMinutes(20).toString()));
        body.put("attachmentIds", List.of(foreign));
        as(a.driver()).body(body).post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(400)
                .body("error.code", is("LOGYX-INC-011"));
        body.put("attachmentIds", List.of(photo));
        String key = "emergencia-" + UUID.randomUUID();
        String id = as(a.driver()).header("Idempotency-Key", key).body(body)
                .post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(201)
                .body("data.severity", is("CRITICAL")).body("data.latitude", is(-15.5f)).extract().path("data.id");
        // Reintento de la cola sin señal: la misma incidencia
        as(a.driver()).header("Idempotency-Key", key).body(body)
                .post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(201).body("data.id", is(id));
        as(a.pyme()).get("/api/v1/incidents/" + id).then().statusCode(200)
                .body("data.attachments.documentId", contains(photo)).body("data.occurredAt", notNullValue());

        Op op = operator();
        as(op.session()).body(Map.of("body", "Revisar póliza", "internal", true))
                .post("/api/v1/incidents/" + id + "/comments").then().statusCode(201);
        as(a.pyme()).body(Map.of("body", "Necesitamos fotos", "internal", true))
                .post("/api/v1/incidents/" + id + "/comments").then().statusCode(403)
                .body("error.code", is("LOGYX-INC-009"));
        as(a.pyme()).body(Map.of("body", "Necesitamos fotos")).post("/api/v1/incidents/" + id + "/comments").then()
                .statusCode(201);
        as(a.pyme()).get("/api/v1/incidents/" + id).then().statusCode(200)
                .body("data.comments", hasSize(1)).body("data.comments[0].internal", is(false))
                .body("data.assigneeName", nullValue());
        as(op.session()).get("/api/v1/incidents/" + id).then().statusCode(200)
                .body("data.comments", hasSize(2)).body("data.comments.internal", hasItem(true));

        // Asignación: a mí; a alguien que no es del operador, no
        as(a.pyme()).body(Map.of()).put("/api/v1/incidents/" + id + "/assignee").then().statusCode(403);
        as(op.session()).body(Map.of("assigneeProfileId", UUID.randomUUID().toString()))
                .put("/api/v1/incidents/" + id + "/assignee").then().statusCode(400)
                .body("error.code", is("LOGYX-INC-012"));
        as(op.session()).body(Map.of()).put("/api/v1/incidents/" + id + "/assignee").then().statusCode(200)
                .body("data.assigneeProfileId", is(op.profileId())).body("data.assigneeName", is("Operador 360"));
        as(op.session()).get("/api/v1/operator/incidents?assignee=me").then().statusCode(200)
                .body("data.id", hasItem(id));
        as(op.session()).get("/api/v1/operator/incidents?severity=CRITICAL&order=due").then().statusCode(200)
                .body("data.id", hasItem(id));

        // Resolver y reabrir
        as(op.session()).body(Map.of("status", "RESOLVED", "resolutionNotes", "Sin daños", "shipmentOutcome", "RESUME"))
                .post("/api/v1/incidents/" + id + "/status").then().statusCode(200);
        as(a.pyme()).body(Map.of("reason", "x")).post("/api/v1/incidents/" + id + "/reopen").then().statusCode(403);
        as(op.session()).body(Map.of("reason", "La PYME reporta daños"))
                .post("/api/v1/incidents/" + id + "/reopen").then().statusCode(200)
                .body("data.status", is("IN_REVIEW")).body("data.reopenCount", is(1)).body("data.holdsShipment", is(true));
        as(op.session()).body(Map.of("reason", "Otra vez")).post("/api/v1/incidents/" + id + "/reopen").then()
                .statusCode(409).body("error.code", is("LOGYX-INC-008"));
    }

    @Test
    void laDisputaDePagoSeVinculaAUnAjusteYNoDetieneElViaje() {
        Flows.Awarded a = Flows.inTransit("Disputa");
        String adj = as(a.carrier()).body(Map.of("type", "TOLL", "amount", 50))
                .post("/api/v1/shipments/" + a.shipmentId() + "/adjustments").then().statusCode(201)
                .extract().path("data.id");
        as(a.pyme()).body(Map.of("note", "No corresponde")).post("/api/v1/adjustments/" + adj + "/reject").then()
                .statusCode(200);
        String id = incident(a.carrier(), a.shipmentId(), Map.of("category", "PAYMENT_DISPUTE",
                "description", "El peaje sí se pagó", "adjustmentId", adj, "claimedAmount", 50));
        as(a.pyme()).get("/api/v1/incidents/" + id).then().statusCode(200)
                .body("data.adjustmentId", is(adj)).body("data.blocking", is(false));
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        // Una informativa se resuelve sin estado final del envío
        Op op = operator();
        as(op.session()).body(Map.of("status", "RESOLVED", "resolutionNotes", "Se paga el peaje"))
                .post("/api/v1/incidents/" + id + "/status").then().statusCode(200).body("data.status", is("RESOLVED"));
    }

    // ------------------------------------------------------------------ 2.15

    @Test
    void fichaTreintaYSeisSoloParaElOperador() {
        Flows.Awarded a = Flows.delivered("360");
        Op op = operator();
        as(op.session()).get("/api/v1/operator/organizations/" + a.carrier().orgId() + "/overview").then()
                .statusCode(200)
                .body("data.type", is("CARRIER"))
                .body("data.users.role", hasItems("ADMIN", "DRIVER"))
                .body("data.vehicles", hasSize(greaterThanOrEqualTo(1)))
                .body("data.counts.shipmentsDelivered", is(1))
                .body("data.recentShipments.id", hasItem(a.shipmentId()))
                .body("data.reputation.trustScore", notNullValue())
                .body("data.verificationHistory", notNullValue())
                .body("data.activity", not(empty()));
        as(a.pyme()).get("/api/v1/operator/organizations/" + a.carrier().orgId() + "/overview").then().statusCode(403);
        as(a.carrier()).get("/api/v1/operator/signals").then().statusCode(403);
        as(op.session()).get("/api/v1/operator/organizations/" + UUID.randomUUID() + "/overview").then().statusCode(404);
    }

    @Test
    void lasSenalesMuestranDatosRepetidosEntreOrganizaciones() {
        String phone = "9" + String.format("%08d", System.nanoTime() % 100_000_000L);
        Session one = registerWithPhone("PYME", "Señal uno", phone);
        Session two = registerWithPhone("CARRIER", "Señal dos", "+51" + phone);
        String hash = "f".repeat(60) + String.format("%04d", System.nanoTime() % 10000);
        system.run(() -> em.createNativeQuery("UPDATE organizations SET signup_ip_hash = ?1 WHERE id IN (?2, ?3)")
                .setParameter(1, hash).setParameter(2, UUID.fromString(one.orgId()))
                .setParameter(3, UUID.fromString(two.orgId())).executeUpdate());
        Op op = operator();
        var signals = as(op.session()).get("/api/v1/operator/signals").then().statusCode(200).extract()
                .jsonPath().getList("data", Map.class);
        assertTrue(signals.stream().anyMatch(s -> "SHARED_PHONE".equals(s.get("kind"))
                && phone.equals(s.get("value"))), "teléfono repetido");
        var ip = signals.stream().filter(s -> "SHARED_SIGNUP_IP".equals(s.get("kind"))
                && ((List<?>) s.get("organizations")).stream().anyMatch(o -> one.orgId().equals(((Map<?, ?>) o).get("id"))))
                .findFirst().orElseThrow();
        assertNull(ip.get("value"), "la huella de la IP no se muestra");
        assertEquals("HIGH", ip.get("severity"));
    }

    private Session registerWithPhone(String type, String name, String phone) {
        String email = "senal" + System.nanoTime() + "@test.pe";
        Map<String, Object> body = new java.util.HashMap<>(Map.of("organizationType", type, "legalName",
                name + " SAC", "ruc", Api.newRuc(), "city", "Lima", "phone", phone, "fullName", "Persona " + name,
                "email", email, "password", "Secreta123", "acceptPrivacyPolicy", true));
        body.put("acceptTerms", true);
        String orgId = given().contentType("application/json").body(body).post("/api/v1/auth/register").then()
                .statusCode(201).extract().path("data.user.organizationId");
        Api.markVerified(orgId);
        return login(email, "Secreta123");
    }

    @Test
    void elOperadorSuspendeUnUsuarioYSuAdminNoLoReactiva() {
        Flows.Awarded a = Flows.awarded("Suspension");
        String driverProfile = as(a.driver()).get("/api/v1/auth/me").then().statusCode(200).extract().path("data.id");
        Op op = operator();
        as(op.session()).body(Map.of("reason", "x")).post("/api/v1/operator/users/" + driverProfile + "/suspend").then()
                .statusCode(400);
        as(op.session()).body(Map.of("reason", "Licencia adulterada"))
                .post("/api/v1/operator/users/" + driverProfile + "/suspend").then().statusCode(200)
                .body("data.active", is(false)).body("data.operatorSuspensionReason", is("Licencia adulterada"));
        // Sus sesiones se cerraron y no puede entrar
        as(a.driver()).get("/api/v1/driver/shipments/active").then().statusCode(401);
        given().contentType("application/json").body(Map.of("email", emailOf(driverProfile), "password", "Chofer1234"))
                .post("/api/v1/auth/login").then().statusCode(401);
        // Su ADMIN no lo reactiva
        String driverId = as(a.carrier()).get("/api/v1/drivers").then().statusCode(200).extract()
                .path("data.find { it.email == '" + a.driver().email() + "' }.id");
        assertNotNull(driverId);
        as(a.carrier()).post("/api/v1/drivers/" + driverId + "/reactivate").then().statusCode(409)
                .body("error.code", is("LOGYX-USR-007"));
        as(a.carrier()).body(Map.of("reason", "x")).post("/api/v1/operator/users/" + driverProfile + "/reactivate")
                .then().statusCode(403);
        as(op.session()).body(Map.of()).post("/api/v1/operator/users/" + driverProfile + "/reactivate").then()
                .statusCode(200).body("data.active", is(true)).body("data.operatorSuspendedAt", nullValue());
        // Las cuentas del operador no se suspenden desde aquí
        as(op.session()).body(Map.of("reason", "Prueba"))
                .post("/api/v1/operator/users/" + op.profileId() + "/suspend").then().statusCode(409)
                .body("error.code", is("LOGYX-USR-008"));
    }

    private String emailOf(String profileId) {
        return system.call(() -> (String) em.createNativeQuery("SELECT email FROM profiles WHERE id = ?1")
                .setParameter(1, UUID.fromString(profileId)).getSingleResult());
    }

    @Test
    void cambioDeRazonSocialYRucConDocumentoCreaLaSucesora() {
        Session carrier = register("CARRIER", "Sucesion");
        String vehicleId = vehicle(carrier, plate(), 5000);
        Op op = operator();
        String path = "/api/v1/operator/organizations/" + carrier.orgId() + "/legal-change";
        // Sin documento, no
        raw(op.session()).multiPart("legalName", "Nuevo Nombre SAC").multiPart("reason", "Ficha RUC").post(path).then()
                .statusCode(400);
        // Misma RUC: corrige la razón social
        raw(op.session()).multiPart("legalName", "Transportes Corregidos SAC").multiPart("reason", "Error de tipeo")
                .multiPart("file", "ficha.pdf", PDF, "application/pdf").post(path).then().statusCode(200)
                .body("data.successorCreated", is(false)).body("data.organizationId", is(carrier.orgId()));
        // RUC nuevo: sucesora verificada con las personas y la flota
        String newRuc = Api.newRuc();
        String successor = raw(op.session()).multiPart("ruc", newRuc).multiPart("reason", "Cambio de razón social en SUNAT", "text/plain; charset=UTF-8")
                .multiPart("file", "ficha.pdf", PDF, "application/pdf").post(path).then().statusCode(200)
                .body("data.successorCreated", is(true)).extract().path("data.organizationId");
        assertNotEquals(carrier.orgId(), successor);
        as(op.session()).get("/api/v1/operator/organizations/" + successor + "/overview").then().statusCode(200)
                .body("data.ruc", is(newRuc)).body("data.verificationStatus", is("VERIFIED"))
                .body("data.predecessorId", is(carrier.orgId())).body("data.vehicles.id", hasItem(vehicleId))
                .body("data.users", hasSize(1)).body("data.legalChanges", hasSize(1));
        as(op.session()).get("/api/v1/operator/organizations/" + carrier.orgId() + "/overview").then().statusCode(200)
                .body("data.verificationStatus", is("SUSPENDED")).body("data.successorIds", hasItem(successor))
                .body("data.legalChanges", hasSize(2));
        // El usuario movido vuelve a iniciar sesión (su JWT tenía la organización anterior)
        as(carrier).get("/api/v1/vehicles").then().statusCode(401);
        Session again = login(carrier.email(), "Secreta123");
        assertEquals(successor, again.orgId());
        String changeId = as(op.session()).get("/api/v1/operator/organizations/" + successor + "/overview").then()
                .extract().path("data.legalChanges[0].id");
        raw(op.session()).header("Accept", "*/*").get("/api/v1/operator/legal-changes/" + changeId + "/file").then()
                .statusCode(200).contentType("application/pdf");
        // El RUC ya usado no se repite
        raw(op.session()).multiPart("ruc", newRuc).multiPart("reason", "Otra vez")
                .multiPart("file", "ficha.pdf", PDF, "application/pdf")
                .post("/api/v1/operator/organizations/" + register("CARRIER", "Otra sucesion").orgId() + "/legal-change")
                .then().statusCode(409).body("error.code", is("LOGYX-ORG-002"));
    }

    @Test
    void elCambioDeRucExigeQueNoHayaCompromisosVivos() {
        Flows.Awarded a = Flows.awarded("Sucesion viva");
        Op op = operator();
        raw(op.session()).multiPart("ruc", Api.newRuc()).multiPart("reason", "Cambio de RUC")
                .multiPart("file", "ficha.pdf", PDF, "application/pdf")
                .post("/api/v1/operator/organizations/" + a.carrier().orgId() + "/legal-change").then().statusCode(409)
                .body("error.code", is("LOGYX-ORG-008"));
    }

    @Test
    void pedirAccesoDesdeElRegistroAvisaAlAdminSinRevelarNada() {
        Session pyme = register("PYME", "Acceso publico");
        String ruc = system.call(() -> (String) em.createNativeQuery("SELECT ruc FROM organizations WHERE id = ?1")
                .setParameter(1, UUID.fromString(pyme.orgId())).getSingleResult());
        given().contentType("application/json").body(Map.of("ruc", ruc, "fullName", "Luis Ramos",
                        "email", "luis" + System.nanoTime() + "@test.pe"))
                .post("/api/v1/public/organization-access-requests").then().statusCode(202)
                .body("data.accepted", is(true));
        var mail = Mailbox.await(pyme.email(), 1);
        assertTrue(mail.getSubject().contains("Piden unirse"), mail.getSubject());
        assertTrue(mail.getText().contains("Luis Ramos"));
        // RUC inexistente: misma respuesta
        given().contentType("application/json").body(Map.of("ruc", Api.newRuc(), "fullName", "Luis Ramos",
                        "email", "luis@test.pe"))
                .post("/api/v1/public/organization-access-requests").then().statusCode(202)
                .body("data.accepted", is(true));
    }
}
