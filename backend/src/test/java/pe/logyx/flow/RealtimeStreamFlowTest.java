package pe.logyx.flow;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;
import pe.logyx.support.SseClient;
import pe.logyx.support.SseClient.Frame;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Tiempo real por SSE (ADR-0007): aislamiento estricto por organización / conductor, reanudación con Last-Event-ID,
 * latido, límite de conexiones y cierre al vencer el JWT. Los eventos llegan por outbox → proyección → NOTIFY →
 * listener → hub (el mismo camino que con varias réplicas).
 */
@QuarkusTest
class RealtimeStreamFlowTest {

    private static final long WAIT = 6000;
    /** Cuánto esperamos para afirmar que algo NO llegó (el camino feliz tarda decenas de ms). */
    private static final long QUIET = 1500;

    @TestHTTPResource("/")
    URL base;

    @Inject SystemTx systemTx;

    private SseClient open(Session s) {
        SseClient c = SseClient.open(base.toString(), s.token(), null);
        assertEquals(200, c.status());
        c.ready();
        return c;
    }

    private static boolean about(Frame f, String key, String value) {
        return f.data() != null && value.equals(String.valueOf(f.data().get(key)));
    }

    private static List<Frame> domainFrames(SseClient c) {
        return c.frames().stream().filter(f -> f.type() != null && !f.type().startsWith("stream.")).toList();
    }

    @Test
    void ofertaLlegaSoloALaPymeDuenaYElConteoALosTransportistas() {
        Session pyme = register("PYME", "RT Pyme Duena");
        Session otherPyme = register("PYME", "RT Pyme Ajena");
        Session carrier = register("CARRIER", "RT Carrier Oferta");
        Session otherCarrier = register("CARRIER", "RT Carrier Mirón");
        String vehicle = vehicle(carrier, plate(), 8000);
        var req = publish(pyme, 2000).statusCode(201).extract();
        String requestId = req.path("data.id");
        float floor = req.path("data.priceFloor");

        try (SseClient owner = open(pyme); SseClient intruder = open(otherPyme);
             SseClient bidder = open(carrier); SseClient watcher = open(otherCarrier)) {

            String bidId = Api.as(carrier).body(Map.of("vehicleId", vehicle, "amount", (float) Math.ceil(floor) + 200))
                    .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201)
                    .extract().path("data.id");

            Frame placed = owner.await(f -> "bid.placed".equals(f.type()) && about(f, "requestId", requestId), WAIT);
            assertNotNull(placed, "La PYME dueña debe recibir bid.placed");
            assertEquals(bidId, placed.data().get("bidId"));
            assertEquals(1, ((Number) placed.data().get("bidCount")).intValue());
            assertNotNull(placed.id(), "Cada evento lleva id SSE (seq)");
            // Payload mínimo: sin montos, nombres ni ruta
            assertFalse(placed.data().containsKey("amount"));
            assertFalse(placed.data().containsKey("carrierName"));
            assertFalse(placed.data().containsKey("route"));

            Frame notif = owner.await(f -> "notification.created".equals(f.type()) && about(f, "relatedId", requestId), WAIT);
            assertNotNull(notif, "La PYME recibe el aviso de notificación nueva con contador");
            assertEquals(1, ((Number) notif.data().get("unreadCount")).intValue());

            assertNotNull(bidder.await(f -> "bid.placed".equals(f.type()) && about(f, "bidId", bidId), WAIT),
                    "El transportista que ofertó lo ve en sus otras pestañas");
            Frame count = watcher.await(f -> "auction.updated".equals(f.type()) && about(f, "requestId", requestId), WAIT);
            assertNotNull(count, "Los demás transportistas reciben el conteo de la subasta");
            assertEquals(Set.of("requestId", "bidCount", "auctionClosed"), count.data().keySet());

            // Aislamiento: la otra PYME no recibe NADA; el otro transportista no recibe la oferta ni el aviso
            sleep(QUIET);
            assertEquals(List.of(), domainFrames(intruder), "Otra organización no debe recibir eventos ajenos");
            assertTrue(watcher.frames().stream().noneMatch(f -> "bid.placed".equals(f.type())
                    || "notification.created".equals(f.type())), "Un competidor no recibe la oferta ni avisos ajenos");
        }
    }

    @Test
    void conductorRecibeSoloSusEnviosYNoElChatNiLosAvisosDeLaOrganizacion() {
        Flows.Awarded a = Flows.awarded("RT Chofer");
        String otherDriverEmail = "chofer-otro" + System.nanoTime() + "@test.pe";
        Api.as(a.carrier()).body(Map.of("fullName", "Chofer Otro", "email", otherDriverEmail,
                "password", "Chofer1234", "licenseNumber", "Q99887766")).post("/api/v1/drivers").then().statusCode(201);
        Session otherDriver = login(otherDriverEmail, "Chofer1234");
        Session strangerCarrier = register("CARRIER", "RT Carrier Extraño");

        try (SseClient driver = open(a.driver()); SseClient colleague = open(otherDriver);
             SseClient carrier = open(a.carrier()); SseClient pyme = open(a.pyme());
             SseClient stranger = open(strangerCarrier)) {

            Api.as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
            Api.as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200);
            // Chat entre la PYME y el transportista: el conductor no debe verlo
            Api.as(a.pyme()).body(Map.of("body", "¿A qué hora llegan?"))
                    .post("/api/v1/shipment-requests/" + a.requestId() + "/threads/" + a.carrier().orgId() + "/messages")
                    .then().statusCode(201);

            for (SseClient c : List.of(driver, carrier, pyme)) {
                assertNotNull(c.await(f -> "shipment.stop_updated".equals(f.type()) && about(f, "stopId", a.pickupStopId())
                        && about(f, "stopStatus", "COMPLETED"), WAIT), "Las partes y el conductor ven la parada");
                assertNotNull(c.await(f -> "shipment.status_changed".equals(f.type()) && about(f, "status", "IN_TRANSIT"),
                        WAIT), "Las partes y el conductor ven el cambio de estado");
            }
            assertNotNull(carrier.await(f -> "message.posted".equals(f.type()) && about(f, "requestId", a.requestId()), WAIT));
            assertNotNull(pyme.await(f -> "message.posted".equals(f.type()), WAIT));

            sleep(QUIET);
            assertEquals(List.of(), domainFrames(colleague), "Otro conductor de la misma empresa no ve envíos ajenos");
            assertEquals(List.of(), domainFrames(stranger), "Otra organización no recibe nada");
            assertTrue(driver.frames().stream().noneMatch(f -> "message.posted".equals(f.type())
                    || "notification.created".equals(f.type())), "El conductor no recibe el chat ni los avisos de la org");
        }
    }

    @Test
    void reanudaConLastEventIdSoloConLoPropio() {
        Flows.Bidding b = Flows.bidding("RT Reanuda");
        Session otherPyme = register("PYME", "RT Reanuda Ajena");
        String lastId;
        try (SseClient first = open(b.pyme())) {
            lastId = first.frames().getFirst().id();
            assertNotNull(lastId, "stream.ready lleva el último id visible");
        }
        // Mientras está desconectada, otro transportista oferta
        Session late = register("CARRIER", "RT Reanuda Tarde");
        String v = vehicle(late, plate(), 8000);
        String lateBid = Api.as(late).body(Map.of("vehicleId", v, "amount", b.above(500)))
                .post("/api/v1/shipment-requests/" + b.requestId() + "/bids").then().statusCode(201)
                .extract().path("data.id");
        // El evento queda proyectado (despacho inmediato tras el commit)
        awaitProjected(lateBid);

        try (SseClient resumed = SseClient.open(base.toString(), b.pyme().token(), lastId)) {
            assertEquals(200, resumed.status());
            Frame ready = resumed.ready();
            assertEquals("stream.ready", ready.type());
            List<Frame> before = resumed.frames().subList(0, resumed.frames().indexOf(ready));
            assertTrue(before.stream().anyMatch(f -> "bid.placed".equals(f.type()) && about(f, "bidId", lateBid)),
                    "Al reanudar se reenvía lo ocurrido desde Last-Event-ID, antes de stream.ready");
            assertTrue(before.stream().allMatch(f -> Long.parseLong(f.id()) > Long.parseLong(lastId)));
        }

        // Otra organización con Last-Event-ID = 0 (todo) no recibe nada de la PYME dueña: la reanudación lee bajo RLS
        try (SseClient other = SseClient.open(base.toString(), otherPyme.token(), "0")) {
            assertEquals(200, other.status());
            Frame ready = other.ready();
            assertEquals(0, ((Number) ready.data().get("replayed")).intValue());
            sleep(300);
            assertEquals(List.of(), domainFrames(other));
        }
    }

    @Test
    void reanudacionDemasiadoAntiguaPideResincronizar() {
        Session pyme = register("PYME", "RT Resync");
        Session carrier = register("CARRIER", "RT Resync Carrier");
        String v = vehicle(carrier, plate(), 30000);
        // replay-limit en test = 20: 11 solicitudes con oferta = 22 eventos (bid.placed + notification.created)
        String last = null;
        for (int i = 0; i < 11; i++) {
            var r = publish(pyme, 1000).statusCode(201).extract();
            float floor = r.path("data.priceFloor");
            last = Api.as(carrier).body(Map.of("vehicleId", v, "amount", (float) Math.ceil(floor) + 100))
                    .post("/api/v1/shipment-requests/" + r.path("data.id") + "/bids").then().statusCode(201)
                    .extract().path("data.id");
        }
        awaitProjected(last);
        try (SseClient c = SseClient.open(base.toString(), pyme.token(), "0")) {
            assertEquals("stream.resync", c.ready().type());
        }
    }

    @Test
    void sinTokenEs401YHayLimiteDeConexionesPorUsuario() {
        assertEquals(401, SseClient.open(base.toString(), null, null).status());
        Session pyme = register("PYME", "RT Limite");
        List<SseClient> open = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) open.add(open(pyme));
            SseClient sixth = SseClient.open(base.toString(), pyme.token(), null);
            assertEquals(429, sixth.status(), "La sexta conexión del mismo usuario se rechaza");
            // Otro usuario no se ve afectado
            try (SseClient other = open(register("PYME", "RT Limite Otro"))) {
                assertEquals(200, other.status());
            }
        } finally {
            open.forEach(SseClient::close);
        }
    }

    @Test
    void latidoYCierreLimpioAlVencerElJwt() {
        Session pyme = register("PYME", "RT Expira");
        try (SseClient c = open(pyme)) {
            sleep(2500);
            assertTrue(c.comments() >= 1, "Latido como comentario SSE");
        }
        String shortLived = reissue(pyme.token(), 3);
        try (SseClient c = SseClient.open(base.toString(), shortLived, null)) {
            assertEquals(200, c.status());
            c.ready();
            Frame expired = c.awaitType("stream.expired", 8000);
            assertNotNull(expired, "Al vencer el JWT llega stream.expired");
            long until = System.currentTimeMillis() + 3000;
            while (!c.ended() && System.currentTimeMillis() < until) sleep(100);
            assertTrue(c.ended(), "Y el servidor cierra la conexión");
        }
    }

    @Test
    void fanOutPasaPorPostgresYUnNotifyForjadoNoCambiaElDestinatario() {
        Session pyme = register("PYME", "RT FanOut A");
        Session other = register("PYME", "RT FanOut B");
        String tag = "fan-" + System.nanoTime();
        try (SseClient a = open(pyme); SseClient b = open(other)) {
            // Fila escrita directamente en la BD (como lo haría otra réplica): llega solo por NOTIFY → listener → hub
            Number seq = systemTx.call(() -> (Number) em().createNativeQuery("INSERT INTO realtime_events "
                    + "(event_type, org_id, payload) VALUES ('notification.created', ?1, CAST(?2 AS jsonb)) RETURNING seq")
                    .setParameter(1, java.util.UUID.fromString(pyme.orgId()))
                    .setParameter(2, "{\"tag\":\"" + tag + "\"}").getSingleResult());
            assertNotNull(a.await(f -> about(f, "tag", tag), WAIT), "Llega por LISTEN/NOTIFY");
            // Otra organización forja un NOTIFY con ese seq: el hub lee la fila real y respeta su destinatario
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                em().createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', 'PYME', true)")
                        .setParameter(1, other.orgId()).getSingleResult();
                em().createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
                em().createNativeQuery("SELECT pg_notify('logyx_realtime', ?1)").setParameter(1, seq.toString())
                        .getSingleResult();
            });
            sleep(QUIET);
            assertEquals(List.of(), domainFrames(b), "Un NOTIFY forjado no entrega eventos ajenos");
        }
    }

    private static jakarta.persistence.EntityManager em() {
        return io.quarkus.arc.Arc.container().instance(jakarta.persistence.EntityManager.class).get();
    }

    /** Mismo usuario y claims, pero con vencimiento en {@code seconds}. */
    @SuppressWarnings("unchecked")
    private static String reissue(String token, long seconds) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
            Map<String, Object> c = new com.fasterxml.jackson.databind.ObjectMapper().readValue(payload, Map.class);
            return Jwt.issuer((String) c.get("iss")).subject((String) c.get("sub")).upn((String) c.get("upn"))
                    .groups(Set.copyOf((List<String>) c.get("groups")))
                    .claim("org_id", c.get("org_id")).claim("org_type", c.get("org_type")).claim("role", c.get("role"))
                    .expiresIn(seconds).sign();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void awaitProjected(String bidId) {
        long until = System.currentTimeMillis() + WAIT;
        while (System.currentTimeMillis() < until) {
            Number n = systemTx.call(() -> (Number) io.quarkus.arc.Arc.container().instance(jakarta.persistence.EntityManager.class)
                    .get().createNativeQuery("SELECT count(*) FROM realtime_events WHERE payload->>'bidId' = ?1")
                    .setParameter(1, bidId).getSingleResult());
            if (n.longValue() > 0) return;
            sleep(100);
        }
        fail("El evento no se proyectó");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
