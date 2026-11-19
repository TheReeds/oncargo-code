package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * RLS de la Ola 2 zona X (V23), directamente en PostgreSQL y con consultas que NO filtran por organización: ajustes
 * del envío (solo las partes, el conductor asignado y el operador; solo cambian estado y decisión), notas internas de
 * incidencias (solo el operador), adjuntos, cambios legales (solo el operador) y suspensión de usuarios.
 */
@QuarkusTest
class ExecutionRowLevelSecurityTest {

    @Inject EntityManager em;
    @Inject AuthService auth;

    private static String profileOf(Session s) {
        String payload = s.token().split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("token sin sub");
        return m.group(1);
    }

    private <T> T as(Session s, String orgType, java.util.function.Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                            + "set_config('app.profile_id', ?3, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return work.get();
        });
    }

    private long countAs(Session s, String orgType, String sql) {
        return as(s, orgType, () -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }

    private int executeAs(Session s, String orgType, String sql) {
        return as(s, orgType, () -> em.createNativeQuery(sql).executeUpdate());
    }

    private Session operator() {
        var r = auth.provisionOperator("RLS X " + System.nanoTime() % 1000, Api.newRuc(), "Op X",
                "opx" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    @Test
    void losAjustesSoloLosVenYDecidenLasPartesYNoSeReescriben() {
        Flows.Awarded a = Flows.inTransit("RLS Ajustes");
        Session otherPyme = register("PYME", "RLS Ajustes otra pyme");
        Session otherCarrier = register("CARRIER", "RLS Ajustes otro carrier");
        String[] otherDriver = driver(a.carrier(), "Chofer ajeno RLS");
        Session foreignDriver = login(otherDriver[1], "Chofer1234");
        Session op = operator();
        Api.as(a.carrier()).body(Map.of("type", "TOLL", "amount", 30))
                .post("/api/v1/shipments/" + a.shipmentId() + "/adjustments").then().statusCode(201);
        String all = "SELECT count(*) FROM shipment_adjustments WHERE shipment_id = '" + a.shipmentId() + "'";
        assertEquals(1L, countAs(a.pyme(), "PYME", all));
        assertEquals(1L, countAs(a.carrier(), "CARRIER", all));
        assertEquals(1L, countAs(a.driver(), "CARRIER", all), "el conductor asignado");
        assertEquals(1L, countAs(op, "OPERATOR", all));
        assertEquals(0L, countAs(otherPyme, "PYME", all));
        assertEquals(0L, countAs(otherCarrier, "CARRIER", all));
        assertEquals(0L, countAs(foreignDriver, "CARRIER", all), "otro conductor de la misma flota no");
        // Nadie ajeno decide; las partes no reescriben el monto ni nadie borra
        assertEquals(0, executeAs(otherPyme, "PYME", "UPDATE shipment_adjustments SET status = 'ACCEPTED'"));
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME", "UPDATE shipment_adjustments SET amount = 1"));
        assertThrows(Exception.class, () -> executeAs(a.carrier(), "CARRIER", "DELETE FROM shipment_adjustments"));
        // La PYME no propone (el ajuste lo propone el transportista)
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME", "INSERT INTO shipment_adjustments (id, "
                + "shipment_id, requester_org_id, carrier_org_id, type, amount, proposed_by_org_id, respond_by) VALUES "
                + "(gen_random_uuid(), '" + a.shipmentId() + "', '" + a.pyme().orgId() + "', '" + a.carrier().orgId()
                + "', 'TOLL', 10, '" + a.pyme().orgId() + "', now())"));
        // Otro transportista no propone sobre un envío ajeno
        assertThrows(Exception.class, () -> executeAs(otherCarrier, "CARRIER", "INSERT INTO shipment_adjustments (id, "
                + "shipment_id, requester_org_id, carrier_org_id, type, amount, proposed_by_org_id, respond_by) VALUES "
                + "(gen_random_uuid(), '" + a.shipmentId() + "', '" + a.pyme().orgId() + "', '" + otherCarrier.orgId()
                + "', 'TOLL', 10, '" + otherCarrier.orgId() + "', now())"));
    }

    @Test
    void lasNotasInternasSoloLasLeeYEscribeElOperador() {
        Flows.Awarded a = Flows.inTransit("RLS Internas");
        Session op = operator();
        String id = Api.as(a.pyme()).body(Map.of("category", "DELAY", "description", "Vamos tarde"))
                .post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(201)
                .extract().path("data.id");
        Api.as(op).body(Map.of("body", "Interna", "internal", true)).post("/api/v1/incidents/" + id + "/comments")
                .then().statusCode(201);
        String internal = "SELECT count(*) FROM incident_comments WHERE incident_id = '" + id + "' AND internal";
        assertEquals(1L, countAs(op, "OPERATOR", internal));
        assertEquals(0L, countAs(a.pyme(), "PYME", internal));
        assertEquals(0L, countAs(a.carrier(), "CARRIER", internal));
        // Una parte no inserta una interna aunque lo intente por SQL
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME", "INSERT INTO incident_comments (id, "
                + "incident_id, requester_org_id, carrier_org_id, author_org_id, body, internal) VALUES "
                + "(gen_random_uuid(), '" + id + "', '" + a.pyme().orgId() + "', '" + a.carrier().orgId() + "', '"
                + a.pyme().orgId() + "', 'x', true)"));
        // Otra organización no ve la incidencia ni sus adjuntos
        Session otherPyme = register("PYME", "RLS Internas otra");
        assertEquals(0L, countAs(otherPyme, "PYME", "SELECT count(*) FROM incidents WHERE id = '" + id + "'"));
        assertEquals(0L, countAs(otherPyme, "PYME", "SELECT count(*) FROM incident_attachments"));
    }

    @Test
    void cambiosLegalesYSuspensionesSonSoloDelOperador() {
        Session carrier = register("CARRIER", "RLS Legal");
        Session op = operator();
        assertEquals(0L, countAs(carrier, "CARRIER", "SELECT count(*) FROM organization_legal_changes"));
        assertThrows(Exception.class, () -> executeAs(carrier, "CARRIER", "INSERT INTO organization_legal_changes (id, "
                + "organization_id, old_legal_name, new_legal_name, old_ruc, new_ruc, reason, document_key, document_name, "
                + "document_content_type, document_size_bytes, document_sha256) VALUES (gen_random_uuid(), '"
                + carrier.orgId() + "', 'a', 'b', '1', '2', 'r', 'k" + System.nanoTime() + "', 'n', 'application/pdf', 1, 'x')"));
        // El ADMIN no toca la suspensión del operador; el operador no toca más que el estado de un usuario ajeno
        String me = profileOf(carrier);
        assertThrows(Exception.class, () -> executeAs(carrier, "CARRIER",
                "UPDATE profiles SET operator_suspended_at = now() WHERE id = '" + me + "'"));
        assertThrows(Exception.class, () -> executeAs(op, "OPERATOR",
                "UPDATE profiles SET email = 'robo@x.pe' WHERE id = '" + me + "'"));
        assertEquals(1, executeAs(op, "OPERATOR", "UPDATE profiles SET active = false, operator_suspended_at = now(), "
                + "operator_suspension_reason = 'prueba' WHERE id = '" + me + "'"));
        // Otra organización no ve ni toca a esa persona
        Session other = register("PYME", "RLS Legal otra");
        assertEquals(0, executeAs(other, "PYME", "UPDATE profiles SET active = true WHERE id = '" + me + "'"));
    }
}
