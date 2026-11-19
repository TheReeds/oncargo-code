package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** RLS de la zona P (V21): plantillas, programaciones, preferencias, respaldos y bloqueo de solicitudes/ofertas. */
@QuarkusTest
class DemandZoneRlsTest {

    @Inject EntityManager em;
    @Inject AuthService auth;
    @Inject SystemTx systemTx;

    private static String profileOf(Session s) {
        String payload = s.token().split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("token sin sub");
        return m.group(1);
    }

    private long countAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            bind(s, orgType);
            return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
        });
    }

    private int executeAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            bind(s, orgType);
            return em.createNativeQuery(sql).executeUpdate();
        });
    }

    private void bind(Session s, String orgType) {
        em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                        + "set_config('app.profile_id', ?3, true)")
                .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
        em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
    }

    private Session operator() {
        var r = auth.provisionOperator("RLS Op P " + System.nanoTime() % 1000, Api.newRuc(), "Op",
                "op" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    @Test
    void plantillasProgramacionesYBorradoresSonDeSuOrganizacion() {
        Session pyme = register("PYME", "RLS Plantilla");
        Session other = register("PYME", "RLS Plantilla Ajena");
        Session carrier = register("CARRIER", "RLS Plantilla Carrier");
        String id = as(pyme).body(Map.ofEntries(Map.entry("name", "Mía"), Map.entry("originCity", "LIMA"),
                Map.entry("originAddress", "Av. Argentina 1"), Map.entry("destinationCity", "JULIACA"),
                Map.entry("destinationAddress", "Jr. Lima 2"), Map.entry("cargoType", "GENERAL"),
                Map.entry("cargoDescription", "Abarrotes"), Map.entry("weightKg", 1000)))
                .post("/api/v1/request-templates").then().statusCode(201).extract().path("data.id");
        as(pyme).body(Map.of("frequency", "WEEKLY", "pickupWeekday", 1)).put("/api/v1/request-templates/" + id + "/schedule")
                .then().statusCode(200);

        for (String t : new String[]{"request_templates", "request_schedules"}) {
            assertEquals(1, countAs(pyme, "PYME", "SELECT count(*) FROM " + t + " WHERE organization_id = '" + pyme.orgId() + "'"));
            assertEquals(0, countAs(other, "PYME", "SELECT count(*) FROM " + t), t + " de otra organización");
            assertEquals(0, countAs(carrier, "CARRIER", "SELECT count(*) FROM " + t), t + " del transportista");
            assertEquals(1, countAs(operator(), "OPERATOR", "SELECT count(*) FROM " + t + " WHERE organization_id = '"
                    + pyme.orgId() + "'"), t + " lectura del operador");
        }
        // Otra organización no inserta ni cambia a nombre ajeno; el operador solo lee
        assertThrows(Exception.class, () -> executeAs(other, "PYME", "INSERT INTO request_templates (organization_id, created_by, "
                + "name, origin_city, origin_address, destination_city, destination_address, cargo_type, cargo_description, "
                + "weight_kg) VALUES ('" + pyme.orgId() + "', '" + profileOf(pyme) + "', 'x', 'LIMA', 'a', 'JULIACA', 'b', "
                + "'GENERAL', 'c', 10)"));
        assertEquals(0, executeAs(other, "PYME", "UPDATE request_templates SET name = 'robada' WHERE id = '" + id + "'"));
        assertEquals(0, executeAs(operator(), "OPERATOR", "UPDATE request_templates SET name = 'op' WHERE id = '" + id + "'"));
        // Sin DELETE de plantillas
        assertEquals(0, executeAs(pyme, "PYME", "DELETE FROM request_templates WHERE id = '" + id + "'"));
    }

    @Test
    void lasPreferenciasSonSoloDeLaPymeYElTransportistaNoLasLee() {
        Session pyme = register("PYME", "RLS Pref Pyme");
        Session carrier = register("CARRIER", "RLS Pref Carrier");
        Session other = register("PYME", "RLS Pref Otra");
        as(pyme).body(Map.of("kind", "BLOCKED")).put("/api/v1/carrier-preferences/" + carrier.orgId()).then().statusCode(200);

        assertEquals(1, countAs(pyme, "PYME", "SELECT count(*) FROM carrier_preferences"));
        assertEquals(0, countAs(carrier, "CARRIER", "SELECT count(*) FROM carrier_preferences"),
                "el transportista no puede enterarse de que lo bloquearon");
        assertEquals(0, countAs(other, "PYME", "SELECT count(*) FROM carrier_preferences"));
        // La función solo responde para el par (PYME, mi organización) y no deja consultar otras
        assertEquals(1, countAs(carrier, "CARRIER", "SELECT CASE WHEN app_is_blocked_by('" + pyme.orgId() + "') THEN 1 ELSE 0 END"));
        assertEquals(0, countAs(carrier, "CARRIER", "SELECT CASE WHEN app_is_blocked_by('" + other.orgId() + "') THEN 1 ELSE 0 END"));
        assertEquals(0, countAs(other, "PYME", "SELECT CASE WHEN app_is_blocked_by('" + pyme.orgId() + "') THEN 1 ELSE 0 END"));
        // Una PYME no marca a nombre de otra
        assertThrows(Exception.class, () -> executeAs(other, "PYME", "INSERT INTO carrier_preferences (requester_org_id, "
                + "carrier_org_id, kind) VALUES ('" + pyme.orgId() + "', '" + other.orgId() + "', 'BLOCKED')"));
        // El transportista no inserta marcas
        assertThrows(Exception.class, () -> executeAs(carrier, "CARRIER", "INSERT INTO carrier_preferences (requester_org_id, "
                + "carrier_org_id, kind) VALUES ('" + pyme.orgId() + "', '" + carrier.orgId() + "', 'PREFERRED')"));

        // Bloqueado: no ve las solicitudes publicadas de la PYME ni inserta ofertas sobre ellas
        String requestId = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        assertEquals(0, countAs(carrier, "CARRIER", "SELECT count(*) FROM shipment_requests WHERE id = '" + requestId + "'"));
        Session free = register("CARRIER", "RLS Pref Libre");
        assertEquals(1, countAs(free, "CARRIER", "SELECT count(*) FROM shipment_requests WHERE id = '" + requestId + "'"));
        String vehicle = vehicle(carrier, plate(), 8000);
        String auctionId = String.valueOf(systemTx.call(() -> em.createNativeQuery(
                "SELECT id FROM auctions WHERE request_id = ?1").setParameter(1, UUID.fromString(requestId)).getSingleResult()));
        assertThrows(Exception.class, () -> executeAs(carrier, "CARRIER", "INSERT INTO bids (id, auction_id, request_id, "
                + "requester_org_id, carrier_org_id, vehicle_id, amount, status) VALUES (gen_random_uuid(), '" + auctionId
                + "', '" + requestId + "', '" + pyme.orgId() + "', '" + carrier.orgId() + "', '" + vehicle + "', 99999, 'PENDING')"));
    }

    @Test
    void losRespaldosSonDeLaPymeYElTransportistaSoloVeLosQueLeSolicitaron() {
        Session pyme = register("PYME", "RLS Resp Pyme");
        Session first = register("CARRIER", "RLS Resp Primero");
        Session second = register("CARRIER", "RLS Resp Segundo");
        Session other = register("PYME", "RLS Resp Otra");
        String requestId = publish(pyme, 1500).statusCode(201).extract().path("data.id");
        String firstBid = bid(first, requestId, vehicle(first, plate(), 8000), 100);
        String secondBid = bid(second, requestId, vehicle(second, plate(), 8000), 200);
        as(pyme).post("/api/v1/bids/" + firstBid + "/accept").then().statusCode(200);

        assertEquals(1, countAs(pyme, "PYME", "SELECT count(*) FROM bid_backups"));
        assertEquals(0, countAs(other, "PYME", "SELECT count(*) FROM bid_backups"));
        assertEquals(0, countAs(second, "CARRIER", "SELECT count(*) FROM bid_backups"), "AVAILABLE: no sabe que es respaldo");
        assertEquals(0, countAs(first, "CARRIER", "SELECT count(*) FROM bid_backups"));
        assertEquals(1, countAs(operator(), "OPERATOR", "SELECT count(*) FROM bid_backups WHERE bid_id = '" + secondBid + "'"));
        // El transportista no se la auto-confirma ni toca otras columnas
        assertEquals(0, executeAs(second, "CARRIER", "UPDATE bid_backups SET state = 'CONFIRMED' WHERE bid_id = '" + secondBid + "'"));

        as(pyme).body(Map.of("reason", "No llegó")).post("/api/v1/shipments/" + as(pyme).get("/api/v1/shipments").then()
                .extract().path("data[0].id") + "/cancel").then().statusCode(200);
        as(pyme).post("/api/v1/bids/" + secondBid + "/backup/request").then().statusCode(200);
        assertEquals(1, countAs(second, "CARRIER", "SELECT count(*) FROM bid_backups"));
        assertThrows(Exception.class, () -> executeAs(second, "CARRIER", "UPDATE bid_backups SET rank = 1, "
                + "state = 'CONFIRMED' WHERE bid_id = '" + secondBid + "'"), "columnas fijas");
        assertThrows(Exception.class, () -> executeAs(second, "CARRIER", "UPDATE bid_backups SET state = 'USED' "
                + "WHERE bid_id = '" + secondBid + "'"), "el transportista solo confirma o declina");
        assertEquals(1, executeAs(second, "CARRIER", "UPDATE bid_backups SET state = 'CONFIRMED', responded_at = now() "
                + "WHERE bid_id = '" + secondBid + "'"));
        assertEquals(0, executeAs(first, "CARRIER", "UPDATE bid_backups SET state = 'DECLINED' WHERE bid_id = '" + secondBid + "'"));
        assertEquals(0, executeAs(other, "PYME", "UPDATE bid_backups SET state = 'USED' WHERE bid_id = '" + secondBid + "'"));
        assertNotNull(LocalDate.now());
    }
}
