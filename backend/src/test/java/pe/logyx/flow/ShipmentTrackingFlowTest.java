package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Zona B del roadmap v2 (Ola 1): contactos de parada tras adjudicar (1.3), filtros y CSV de envíos (1.5), enlace
 * público de seguimiento sin fugas (1.6) y conductor robusto: idempotencia y hora del dispositivo (1.11).
 */
@QuarkusTest
class ShipmentTrackingFlowTest {

    @Inject AuthService auth;
    @Inject SystemTx system;
    @Inject EntityManager em;

    private record Scenario(Session pyme, Session carrier, Session driver, String requestId, String shipmentId,
                            String pickupStopId, String deliveryStopId, String plate, String code) {
        String stop(String stopId, String action) {
            return "/api/v1/shipments/" + shipmentId + "/stops/" + stopId + "/" + action;
        }
    }

    private static final String PICKUP_PHONE = "987654321";
    private static final String DELIVERY_PHONE = "912345678";

    /** Solicitud con contactos y referencia, adjudicada y con conductor asignado. */
    private Scenario awardedWithContacts(String tag) {
        Session pyme = register("PYME", "Pyme " + tag);
        Session carrier = register("CARRIER", "Carrier " + tag);
        String plate = plate();
        String vehicle = vehicle(carrier, plate, 8000);
        Map<String, Object> body = new HashMap<>(Map.of(
                "originCity", "LIMA", "originAddress", "Av. Argentina 1234, Callao",
                "destinationCity", "JULIACA", "destinationAddress", "Jr. San Román 456",
                "cargoType", "GENERAL", "cargoDescription", "Abarrotes " + tag,
                "weightKg", 2000, "pickupDate", LocalDate.now().plusDays(2).toString()));
        body.putAll(Map.of("customerReference", "PED-" + tag.hashCode(),
                "pickupContactName", "Rosa Almacén", "pickupContactPhone", PICKUP_PHONE,
                "pickupWindowFrom", "08:00", "pickupWindowTo", "12:00", "pickupInstructions", "Puerta 3, preguntar por Rosa",
                "deliveryContactName", "Juan Recepción", "deliveryContactPhone", DELIVERY_PHONE,
                "deliveryWindowFrom", "09:00", "deliveryWindowTo", "17:00"));
        var req = as(pyme).body(body).post("/api/v1/shipment-requests").then().statusCode(201).extract();
        String requestId = req.path("data.id");
        String code = req.path("data.code");
        String bidId = bid(carrier, requestId, vehicle, 300);
        String shipmentId = as(pyme).post("/api/v1/bids/" + bidId + "/accept").then().statusCode(200)
                .extract().path("data.shipmentId");
        String[] driver = driver(carrier, "Chofer " + tag);
        as(carrier).body(Map.of("driverId", driver[0])).put("/api/v1/shipments/" + shipmentId + "/driver")
                .then().statusCode(200);
        Session driverSession = login(driver[1], "Chofer1234");
        List<String> stops = as(driverSession).get("/api/v1/shipments/" + shipmentId).then().statusCode(200)
                .extract().path("data.stops.id");
        return new Scenario(pyme, carrier, driverSession, requestId, shipmentId, stops.get(0), stops.get(1), plate, code);
    }

    private Session member(Session admin, Profile.Role role) {
        String email = role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Usuario " + role, role);
        return login(email, "Miembro1234");
    }

    private Session operator() {
        var r = auth.provisionOperator("Op B " + System.nanoTime() % 1000, Api.newRuc(), "Op",
                "opb" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    // ----- 1.3 Contactos tras adjudicar

    @Test
    void contactosSeCopianAlAdjudicarYLosVenLasPartesElConductorYElOperador() {
        Scenario s = awardedWithContacts("Contactos");
        for (Session who : List.of(s.pyme(), s.carrier(), s.driver(), operator())) {
            as(who).get("/api/v1/shipments/" + s.shipmentId()).then().statusCode(200)
                    .body("data.code", is(s.code()))
                    .body("data.requesterOrgId", is(s.pyme().orgId()))
                    .body("data.carrierOrgId", is(s.carrier().orgId()))
                    .body("data.stops[0].contactName", is("Rosa Almacén"))
                    .body("data.stops[0].contactPhone", is(PICKUP_PHONE))
                    .body("data.stops[0].windowFrom", is("08:00"))
                    .body("data.stops[0].windowTo", is("12:00"))
                    .body("data.stops[0].instructions", is("Puerta 3, preguntar por Rosa"))
                    .body("data.stops[1].contactName", is("Juan Recepción"))
                    .body("data.stops[1].contactPhone", is(DELIVERY_PHONE))
                    .body("data.stops[1].instructions", nullValue());
        }
        // Otro conductor del MISMO transportista no ve el envío ni sus paradas (RLS stops_driver_scope + aplicación)
        String[] other = driver(s.carrier(), "Otro chofer");
        Session otherDriver = login(other[1], "Chofer1234");
        as(otherDriver).get("/api/v1/shipments/" + s.shipmentId()).then().statusCode(404);
        // Otra PYME tampoco
        as(register("PYME", "Pyme ajena contactos")).get("/api/v1/shipments/" + s.shipmentId()).then().statusCode(404);
    }

    // ----- 1.11 Conductor robusto

    @Test
    void llegarYCompletarSonIdempotentesAunqueElEnvioYaEsteEntregado() {
        Flows.Awarded a = Flows.awarded("Idempotente");
        as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200)
                .body("data.stops[0].status", is("ARRIVED"));
        as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200);
        // Repetir sobre una parada ya completada: 200 con el envío (antes 409 TRK-007)
        as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200)
                .body("data.status", is("IN_TRANSIT"));
        as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        deliveryPhoto(a.driver(), a.shipmentId(), a.deliveryStopId());
        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(200)
                .body("data.status", is("DELIVERED"));
        // Respuesta perdida en ruta: el reintento del cierre final no da TRK-004 (envío cerrado)
        as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(200)
                .body("data.status", is("DELIVERED"));
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        // Una sola entrega auditada y un solo aviso de entrega (sin efectos dobles)
        assertEquals(1L, (long) system.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM audit_log WHERE action = 'SHIPMENT_DELIVERED' AND entity_id = ?1")
                .setParameter(1, UUID.fromString(a.shipmentId())).getSingleResult()).longValue()));
        // Las transiciones inválidas de verdad siguen fallando: completar sin haber llegado
        Flows.Awarded b = Flows.awarded("Idempotente orden");
        as(b.driver()).post(b.stop(b.pickupStopId(), "complete")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-003"));
        as(b.driver()).post(b.stop(b.deliveryStopId(), "arrive")).then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-001"));
    }

    @Test
    void laHoraDelDispositivoSeGuardaJuntoALaDelServidorYSeValida() {
        Flows.Awarded a = Flows.awarded("Hora");
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        // Futura (> 2 min): TRK-008 en occurredAt
        as(a.driver()).body(Map.of("occurredAt", now.plusMinutes(10).toString()))
                .post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(400)
                .body("error.code", is("LOGYX-TRK-008")).body("error.field", is("occurredAt"));
        // Más de 48 h
        as(a.driver()).body(Map.of("occurredAt", now.minusHours(49).toString()))
                .post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(400)
                .body("error.code", is("LOGYX-TRK-008"));
        // Llegó hace 40 min sin señal; el evento se sincroniza ahora
        OffsetDateTime arrived = now.minusMinutes(40);
        as(a.driver()).body(Map.of("occurredAt", arrived.toString()))
                .post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        var stop = as(a.pyme()).get("/api/v1/shipments/" + a.shipmentId()).then().statusCode(200).extract();
        OffsetDateTime arrivedAt = OffsetDateTime.parse(stop.path("data.stops[0].arrivedAt"));
        OffsetDateTime recordedAt = OffsetDateTime.parse(stop.path("data.stops[0].arrivedRecordedAt"));
        assertEquals(arrived.toInstant(), arrivedAt.toInstant());
        assertTrue(recordedAt.isAfter(now.minusMinutes(1)), "la hora del servidor también queda");
        // Monótona: el cierre no puede ser anterior a la llegada
        as(a.driver()).body(Map.of("occurredAt", arrived.minusMinutes(5).toString()))
                .post(a.stop(a.pickupStopId(), "complete")).then().statusCode(400)
                .body("error.code", is("LOGYX-TRK-008"));
        OffsetDateTime picked = now.minusMinutes(20);
        as(a.driver()).body(Map.of("occurredAt", picked.toString()))
                .post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200)
                .body("data.status", is("IN_TRANSIT"));
        // La llegada a destino no puede ser anterior al recojo
        as(a.driver()).body(Map.of("occurredAt", picked.minusMinutes(1).toString()))
                .post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(400)
                .body("error.code", is("LOGYX-TRK-008"));
        // Sin cuerpo sigue valiendo la hora del servidor
        as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200)
                .body("data.stops[1].status", is("ARRIVED"));
    }

    // ----- 1.5 Filtros y CSV

    @Test
    void listaDeEnviosFiltraEnElServidorYExportaCsvConLaMismaRls() {
        Scenario s = awardedWithContacts("Csv");
        Flows.Awarded other = Flows.awarded("Csv ajeno");
        as(s.pyme()).queryParam("q", s.code()).queryParam("page", 0).queryParam("size", 20)
                .get("/api/v1/shipments").then().statusCode(200)
                .body("data.id", contains(s.shipmentId())).body("meta.total", is(1));
        as(s.pyme()).queryParam("q", s.code().replace("-", "").toLowerCase())
                .get("/api/v1/shipments").then().statusCode(200).body("data.id", contains(s.shipmentId()));
        as(s.carrier()).queryParam("q", s.plate()).get("/api/v1/shipments").then().statusCode(200)
                .body("data.id", contains(s.shipmentId()));
        as(s.pyme()).queryParam("status", "DELIVERED,CANCELLED").get("/api/v1/shipments").then().statusCode(200)
                .body("data", empty());
        as(s.pyme()).queryParam("status", "PENDING,IN_TRANSIT").queryParam("originCity", "LIMA")
                .queryParam("fromDate", LocalDate.now().toString()).get("/api/v1/shipments").then().statusCode(200)
                .body("data.id", hasItem(s.shipmentId()));
        as(s.pyme()).queryParam("status", "VOLANDO").get("/api/v1/shipments").then().statusCode(400)
                .body("error.code", is("LOGYX-VAL-001")).body("error.field", is("status"));

        var csv = RestAssured.given().auth().oauth2(s.pyme().token()).get("/api/v1/shipments/export").then()
                .statusCode(200).contentType(containsString("text/csv"))
                .header("Content-Disposition", containsString("envios-")).extract().asByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿Código;Referencia;Origen;Destino"), text);
        assertTrue(text.contains(s.code()));
        assertTrue(text.contains("Transportista"));
        assertTrue(text.contains("Carrier Csv"), "la contraparte de la PYME es el transportista");
        String otherCode = as(other.pyme()).get("/api/v1/shipments/" + other.shipmentId()).then().statusCode(200)
                .extract().path("data.code");
        assertFalse(text.contains(otherCode));
        String carrierCsv = new String(RestAssured.given().auth().oauth2(s.carrier().token())
                .get("/api/v1/shipments/export").then().statusCode(200).extract().asByteArray(), StandardCharsets.UTF_8);
        assertTrue(carrierCsv.contains(";PYME;") && carrierCsv.contains("Pyme Csv"));
        // Otra PYME no exporta los envíos ajenos
        String foreign = new String(RestAssured.given().auth().oauth2(register("PYME", "Pyme Csv ajena").token())
                .get("/api/v1/shipments/export").then().statusCode(200).extract().asByteArray(), StandardCharsets.UTF_8);
        assertFalse(foreign.contains(s.code()));
    }

    // ----- 1.6 Enlace público

    private static final String PUBLIC = "/api/v1/public/tracking/";

    @Test
    void enlacePublicoMuestraSoloLaListaBlancaYNoFiltraDatos() {
        Scenario s = awardedWithContacts("Publico");
        as(s.driver()).post(s.stop(s.pickupStopId(), "arrive")).then().statusCode(200);
        as(s.driver()).post(s.stop(s.pickupStopId(), "complete")).then().statusCode(200);

        var created = as(s.pyme()).body(Map.of("expiresInDays", 5))
                .post("/api/v1/shipments/" + s.shipmentId() + "/share-links").then().statusCode(201)
                .body("data.link.status", is("ACTIVE")).body("data.url", startsWith("http"))
                .body("data.url", containsString("/s/")).extract();
        String token = created.path("data.token");
        assertEquals(22, token.length(), "128 bits en base64url");
        String linkId = created.path("data.link.id");
        assertEquals(token.substring(18), created.path("data.link.tokenHint"));

        // En la BD solo hay hash: el token no aparece en ninguna columna
        assertEquals(0L, (long) system.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM shipment_share_links WHERE token_hash = ?1 OR position(?1 in token_hash) > 0")
                .setParameter(1, token).getSingleResult()).longValue()));

        var pub = RestAssured.given().get(PUBLIC + token).then().statusCode(200)
                .header("X-Robots-Tag", containsString("noindex"))
                .header("Referrer-Policy", is("no-referrer"))
                .header("Cache-Control", containsString("no-store"))
                .body("data.code", is(s.code()))
                .body("data.status", is("IN_TRANSIT"))
                .body("data.originCity", is("LIMA")).body("data.destinationCity", is("JULIACA"))
                .body("data.stops", hasSize(2))
                .body("data.stops[0].status", is("COMPLETED"))
                .body("data.stops[0].completedAt", notNullValue())
                .body("data.route.polyline", not(emptyOrNullString()))
                .body("data.route.attribution", containsString("OpenStreetMap"))
                .body("data.etaFrom", notNullValue()).body("data.etaTo", notNullValue())
                .extract();
        // Lista blanca exacta de campos
        Map<String, Object> data = pub.path("data");
        assertEquals(java.util.Set.of("code", "status", "originCity", "destinationCity", "pickupDate", "deliveredAt",
                "stops", "etaFrom", "etaTo", "route", "updatedAt", "linkExpiresAt"), data.keySet());
        List<Map<String, Object>> stops = pub.path("data.stops");
        assertEquals(java.util.Set.of("sequence", "type", "city", "status", "arrivedAt", "completedAt"),
                stops.get(0).keySet());
        // Y nada sensible en ningún lugar del cuerpo
        String raw = pub.asString();
        for (String secret : List.of(s.plate(), PICKUP_PHONE, DELIVERY_PHONE, "Rosa", "Juan Recepción", "Puerta 3",
                "Av. Argentina", "San Román", "Pyme Publico", "Carrier Publico", "Chofer Publico", s.shipmentId(),
                s.requestId(), s.pyme().orgId(), s.carrier().orgId(), "PED-", "agreedPrice", "Abarrotes",
                "priceBreakdown", "driver", "vehicle")) {
            assertFalse(raw.contains(secret), "el seguimiento público filtró: " + secret);
        }

        // La PYME ve el enlace sin token; cuenta las visitas
        as(s.pyme()).get("/api/v1/shipments/" + s.shipmentId() + "/share-links").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].views", is(1)).body("data[0].lastViewedAt", notNullValue())
                .body("data[0].token", nullValue()).body("data[0].expiresAt", notNullValue());

        // Revocado, inexistente o mal formado: la misma respuesta
        as(s.pyme()).post("/api/v1/shipments/" + s.shipmentId() + "/share-links/" + linkId + "/revoke").then()
                .statusCode(200).body("data.status", is("REVOKED")).body("data.revokedAt", notNullValue());
        as(s.pyme()).post("/api/v1/shipments/" + s.shipmentId() + "/share-links/" + linkId + "/revoke").then()
                .statusCode(200).body("data.status", is("REVOKED"));
        String revoked = RestAssured.given().get(PUBLIC + token).then().statusCode(404)
                .body("error.code", is("LOGYX-SHL-001")).body("error.message", is("Este enlace ya no está disponible"))
                .extract().path("error.message");
        String unknown = RestAssured.given().get(PUBLIC + "AAAAAAAAAAAAAAAAAAAAAA").then().statusCode(404)
                .body("error.code", is("LOGYX-SHL-001")).extract().path("error.message");
        String malformed = RestAssured.given().get(PUBLIC + "abc' OR 1=1").then().statusCode(404)
                .body("error.code", is("LOGYX-SHL-001")).extract().path("error.message");
        assertEquals(revoked, unknown);
        assertEquals(revoked, malformed);
    }

    @Test
    void soloLaPymeDuenaCreaEnlacesYElLectorSoloLosLee() {
        Scenario s = awardedWithContacts("Permisos");
        String base = "/api/v1/shipments/" + s.shipmentId() + "/share-links";
        Session ops = member(s.pyme(), Profile.Role.OPS);
        Session viewer = member(s.pyme(), Profile.Role.VIEWER);
        as(ops).post(base).then().statusCode(201);
        as(viewer).post(base).then().statusCode(403).body("error.code", is("LOGYX-AUTH-007"));
        as(viewer).get(base).then().statusCode(200).body("data", hasSize(1));
        as(s.carrier()).post(base).then().statusCode(403);
        as(s.driver()).get(base).then().statusCode(403);
        as(register("PYME", "Pyme ajena enlaces")).post(base).then().statusCode(404);
        as(register("PYME", "Pyme ajena lista")).get(base).then().statusCode(404);
        as(s.pyme()).body(Map.of("expiresInDays", 31)).post(base).then().statusCode(400)
                .body("error.code", is("LOGYX-VAL-001")).body("error.field", is("expiresInDays"));
        // Un envío cancelado no se comparte
        as(s.pyme()).body(Map.of("reason", "Ya no se necesita")).post("/api/v1/shipments/" + s.shipmentId() + "/cancel")
                .then().statusCode(200);
        as(s.pyme()).post(base).then().statusCode(409).body("error.code", is("LOGYX-SHL-002"));
    }

    @Test
    void elEnlaceVenceSieteDiasDespuesDeLaEntregaYConSuPropiaFecha() {
        Flows.Awarded a = Flows.delivered("Vence");
        String base = "/api/v1/shipments/" + a.shipmentId() + "/share-links";
        String token = as(a.pyme()).post(base).then().statusCode(201).extract().path("data.token");
        RestAssured.given().get(PUBLIC + token).then().statusCode(200).body("data.status", is("DELIVERED"))
                .body("data.deliveredAt", notNullValue()).body("data.etaFrom", nullValue());
        // Simula que la entrega fue hace 8 días
        system.run(() -> em.createNativeQuery("UPDATE shipments SET delivered_at = now() - interval '8 days' WHERE id = ?1")
                .setParameter(1, UUID.fromString(a.shipmentId())).executeUpdate());
        RestAssured.given().get(PUBLIC + token).then().statusCode(404).body("error.code", is("LOGYX-SHL-001"));
        as(a.pyme()).get(base).then().statusCode(200).body("data[0].status", is("EXPIRED"));
        as(a.pyme()).post(base).then().statusCode(409).body("error.code", is("LOGYX-SHL-003"));
    }
}
