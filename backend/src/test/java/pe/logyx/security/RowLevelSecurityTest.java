package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Flows;
import pe.logyx.support.Api.Session;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Verifica las políticas RLS directamente en PostgreSQL, con consultas que
 * deliberadamente NO filtran por organización (simulan un bug de aplicación).
 */
@QuarkusTest
class RowLevelSecurityTest {

    @Inject
    EntityManager em;

    @Inject
    AuthService auth;

    private long countAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
        });
    }

    /** Ejecuta un INSERT/UPDATE como logyx_app con el contexto de {@code s}; devuelve filas afectadas. */
    private int executeAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return em.createNativeQuery(sql).executeUpdate();
        });
    }

    private Session operator() {
        var r = auth.provisionOperator("RLS Operador " + System.nanoTime() % 1000, Api.newRuc(), "Op",
                "op" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    private long countAsOwner(String sql) {
        return QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }

    @Test
    void transportistaNoVeFlotaNiOfertasDeOtro() {
        Session a = register("CARRIER", "RLS Carrier A");
        Session b = register("CARRIER", "RLS Carrier B");
        Session pyme = register("PYME", "RLS Pyme");
        String va = vehicle(a, plate(), 5000);
        vehicle(b, plate(), 5000);
        vehicle(b, plate(), 5000);

        String reqId = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        float price = Api.as(pyme).get("/api/v1/shipment-requests/" + reqId).then().extract().path("data.suggestedPrice");
        Api.as(a).body(Map.of("vehicleId", va, "amount", price)).post("/api/v1/shipment-requests/" + reqId + "/bids")
                .then().statusCode(201);

        String allVehiclesOfBoth = "SELECT count(*) FROM vehicles WHERE organization_id IN ('"
                + a.orgId() + "','" + b.orgId() + "')";
        assertEquals(3, countAsOwner(allVehiclesOfBoth), "sin RLS el dueño ve todo");
        assertEquals(1, countAs(a, "CARRIER", allVehiclesOfBoth), "A solo ve su vehículo");
        assertEquals(2, countAs(b, "CARRIER", allVehiclesOfBoth), "B solo ve los suyos");

        String bidsOfRequest = "SELECT count(*) FROM bids WHERE request_id = '" + reqId + "'";
        assertEquals(1, countAs(a, "CARRIER", bidsOfRequest));
        assertEquals(0, countAs(b, "CARRIER", bidsOfRequest), "B no ve la oferta de A");
        assertEquals(1, countAs(pyme, "PYME", bidsOfRequest), "la PYME ve las ofertas de su solicitud");
    }

    @Test
    void pymeNoVeSolicitudesNiUsuariosDeOtraPyme() {
        Session p1 = register("PYME", "RLS Pyme Uno");
        Session p2 = register("PYME", "RLS Pyme Dos");
        publish(p1, 500).statusCode(201);

        String requestsOfP1 = "SELECT count(*) FROM shipment_requests WHERE requester_org_id = '" + p1.orgId() + "'";
        assertEquals(1, countAs(p1, "PYME", requestsOfP1));
        assertEquals(0, countAs(p2, "PYME", requestsOfP1));

        String profilesOfP1 = "SELECT count(*) FROM profiles WHERE organization_id = '" + p1.orgId() + "'";
        assertEquals(1, countAs(p1, "PYME", profilesOfP1));
        assertEquals(0, countAs(p2, "PYME", profilesOfP1), "no se filtran usuarios ni hashes");
    }

    @Test
    void rlsBloqueaEscriturasEnNombreDeOtraOrganizacion() {
        Session a = register("CARRIER", "RLS Writer A");
        Session b = register("CARRIER", "RLS Writer B");
        var ex = assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', 'CARRIER', true)")
                    .setParameter(1, a.orgId()).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            em.createNativeQuery("INSERT INTO vehicles (organization_id, plate, type, max_weight_kg) "
                    + "VALUES (?1, 'ZZZ-999', 'VAN', 500)").setParameter(1, java.util.UUID.fromString(b.orgId()))
                    .executeUpdate();
        }));
        assertTrue(rootMessage(ex).contains("row-level security"), rootMessage(ex));
    }

    @Test
    void elBackendActivaRlsEnLasPeticionesAutenticadas() {
        Session a = register("CARRIER", "RLS Probe");
        // Si el binder no hiciera SET ROLE, current_user sería el dueño.
        String role = QuarkusTransaction.requiringNew().call(() ->
                (String) em.createNativeQuery("SELECT current_user").getSingleResult());
        assertNotEquals("logyx_app", role, "fuera de una petición se usa el rol dueño");
        Api.as(a).get("/api/v1/vehicles").then().statusCode(200);
    }

    @Test
    void chatYContraOfertasSoloLasVenLasDosPartes() {
        Flows.Bidding b = Flows.bidding("RLS Chat");
        Session other = register("CARRIER", "RLS Chat Otro");
        Session otherPyme = register("PYME", "RLS Chat Otra Pyme");
        String thread = "/api/v1/shipment-requests/" + b.requestId() + "/threads/" + b.carrier().orgId() + "/messages";
        Api.as(b.carrier()).body(Map.of("body", "Consulta")).post(thread).then().statusCode(201);
        Api.as(b.pyme()).body(Map.of("amount", b.above(50))).post("/api/v1/bids/" + b.bidId() + "/counter-offers")
                .then().statusCode(201);

        String msgs = "SELECT count(*) FROM messages WHERE request_id = '" + b.requestId() + "'";
        String cos = "SELECT count(*) FROM counter_offers WHERE request_id = '" + b.requestId() + "'";
        assertEquals(2, countAs(b.carrier(), "CARRIER", msgs), "mensaje + aviso de contra-oferta");
        assertEquals(2, countAs(b.pyme(), "PYME", msgs));
        assertEquals(0, countAs(other, "CARRIER", msgs), "otro transportista no lee el hilo");
        assertEquals(0, countAs(otherPyme, "PYME", msgs));
        assertEquals(2, countAs(operator(), "OPERATOR", msgs), "el operador lee");
        assertEquals(1, countAs(b.carrier(), "CARRIER", cos));
        assertEquals(0, countAs(other, "CARRIER", cos));

        // No se puede escribir en nombre de otro ni en un hilo ajeno; el historial es inmutable
        var ex = assertThrows(Exception.class, () -> executeAs(other, "CARRIER",
                "INSERT INTO messages (request_id, requester_org_id, carrier_org_id, sender_org_id, body) VALUES ('"
                        + b.requestId() + "','" + b.pyme().orgId() + "','" + b.carrier().orgId() + "','"
                        + b.carrier().orgId() + "','falso')"));
        assertTrue(rootMessage(ex).contains("row-level security"), rootMessage(ex));
        var ex2 = assertThrows(Exception.class, () -> executeAs(b.carrier(), "CARRIER", "UPDATE messages SET body = 'x'"));
        assertTrue(rootMessage(ex2).contains("permission denied"), rootMessage(ex2));
    }

    @Test
    void documentosIncidenciasYNotificacionesSonPrivadosReputacionPublica() {
        Session op = operator();
        Flows.Awarded a = Flows.delivered("RLS Docs");
        Session stranger = register("PYME", "RLS Docs Ajena");
        String sid = a.shipmentId();
        Api.as(a.pyme()).body(Map.of("punctuality", 4, "cargoCondition", 4, "communication", 4))
                .post("/api/v1/shipments/" + sid + "/reviews").then().statusCode(201);
        Api.as(a.carrier()).body(Map.of("category", "CARGO_DAMAGE", "description", "Caja abollada"))
                .post("/api/v1/shipments/" + sid + "/incidents").then().statusCode(201);

        String docs = "SELECT count(*) FROM documents WHERE shipment_id = '" + sid + "'";
        assertEquals(1, countAs(a.pyme(), "PYME", docs));
        assertEquals(1, countAs(a.carrier(), "CARRIER", docs));
        assertEquals(0, countAs(stranger, "PYME", docs), "un tercero no ve la evidencia");
        assertEquals(1, countAs(op, "OPERATOR", docs));

        String incidents = "SELECT count(*) FROM incidents WHERE shipment_id = '" + sid + "'";
        assertEquals(1, countAs(a.pyme(), "PYME", incidents));
        assertEquals(0, countAs(stranger, "PYME", incidents));
        assertEquals(1, countAs(op, "OPERATOR", incidents));
        assertEquals(0, executeAs(a.carrier(), "CARRIER", "UPDATE incidents SET status = 'CLOSED' WHERE shipment_id = '"
                + sid + "'"), "las partes no cambian el estado (solo el operador)");
        assertEquals(1, executeAs(op, "OPERATOR", "UPDATE incidents SET status = 'IN_REVIEW' WHERE shipment_id = '"
                + sid + "'"));

        // Reputación pública; solo el recálculo de sistema la escribe
        String reviews = "SELECT count(*) FROM reviews WHERE shipment_id = '" + sid + "'";
        assertEquals(1, countAs(stranger, "PYME", reviews), "las calificaciones son públicas (RF44)");
        assertEquals(1, countAs(stranger, "PYME", "SELECT count(*) FROM reputation_scores WHERE org_id = '"
                + a.carrier().orgId() + "'"));
        var ex = assertThrows(Exception.class, () -> executeAs(a.carrier(), "CARRIER",
                "UPDATE reputation_scores SET trust_score = 100 WHERE org_id = '" + a.carrier().orgId() + "'"));
        assertTrue(rootMessage(ex).contains("permission denied"), rootMessage(ex));
        var ex2 = assertThrows(Exception.class, () -> executeAs(stranger, "PYME",
                "INSERT INTO reviews (shipment_id, requester_org_id, carrier_org_id, reviewer_org_id, reviewed_org_id, "
                        + "direction, punctuality, cargo_condition, communication, rating) VALUES ('" + sid + "','"
                        + a.pyme().orgId() + "','" + a.carrier().orgId() + "','" + a.pyme().orgId() + "','"
                        + a.carrier().orgId() + "','PYME_TO_CARRIER',1,1,1,1)"));
        assertTrue(rootMessage(ex2).contains("row-level security"), rootMessage(ex2));

        // Notificaciones: cada organización solo las suyas; ningún usuario inserta
        String notif = "SELECT count(*) FROM notifications WHERE org_id = '" + a.pyme().orgId() + "'";
        long own = countAs(a.pyme(), "PYME", notif);
        assertTrue(own > 0);
        assertEquals(own, countAsOwner(notif));
        assertEquals(0, countAs(a.carrier(), "CARRIER", notif), "el transportista no lee las de la PYME");
        assertEquals(0, executeAs(a.carrier(), "CARRIER", "UPDATE notifications SET read_at = now() WHERE org_id = '"
                + a.pyme().orgId() + "'"));
        var ex3 = assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME",
                "INSERT INTO notifications (org_id, type, title, body) VALUES ('" + a.pyme().orgId()
                        + "','NEW_BID','x','y')"));
        assertTrue(rootMessage(ex3).contains("permission denied"), rootMessage(ex3));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}
