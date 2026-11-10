package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api;
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

    private long countAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
        });
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

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}
