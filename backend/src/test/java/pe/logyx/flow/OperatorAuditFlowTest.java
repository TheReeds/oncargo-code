package pe.logyx.flow;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** F05: visor de auditoría del operador (`/operator/audit`, `/operator/audit/actions`). */
@QuarkusTest
class OperatorAuditFlowTest {

    @Inject AuthService auth;
    @Inject EntityManager em;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Auditoría " + System.nanoTime() % 1000, Api.newRuc(), "Operador Auditor",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    private long countAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
        });
    }

    @Test
    void filtrosPaginacionYOrdenDelVisor() {
        Session op = operator();
        Session pyme = register("PYME", "Audit Pyme A");
        Session other = register("PYME", "Audit Pyme B");
        String r1 = publishRoute(pyme, "LIMA", "JULIACA", 1000, 2).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(pyme, "AREQUIPA", "LIMA", 1500, 3).statusCode(201).extract().path("data.id");
        publishRoute(other, "LIMA", "PUNO", 1000, 2).statusCode(201);
        String today = LocalDate.now(ZoneId.of("America/Lima")).toString();
        String base = "/api/v1/operator/audit?organizationId=" + pyme.orgId();

        // Por organización: REGISTER + 2 REQUEST_PUBLISHED, con actor y organización resueltos, más recientes primero
        var all = as(op).get(base + "&size=100").then().statusCode(200)
                .body("meta.total", is(3)).body("meta.page", is(0)).body("meta.size", is(100))
                .body("data.action", hasItems("REGISTER", "REQUEST_PUBLISHED"))
                .body("data.organizationId", everyItem(is(pyme.orgId())))
                .body("data.organizationName", everyItem(is("Audit Pyme A")))
                .body("data.organizationType", everyItem(is("PYME")))
                .body("data.actorEmail", everyItem(is(pyme.email())))
                .body("data.actorName", everyItem(is("Admin Audit Pyme A")))
                .extract();
        List<Integer> ids = all.path("data.id");
        List<Integer> sorted = new ArrayList<>(ids);
        sorted.sort(Collections.reverseOrder());
        assertEquals(sorted, ids, "más recientes primero");
        String actorId = all.path("data[0].actorProfileId");
        assertNotNull(actorId);
        assertNotNull(all.path("data[0].occurredAt"));

        // Paginación
        as(op).get(base + "&size=2").then().statusCode(200).body("data", hasSize(2)).body("meta.total", is(3));
        as(op).get(base + "&size=2&page=1").then().statusCode(200).body("data", hasSize(1)).body("meta.page", is(1));
        as(op).get(base + "&size=1&page=5").then().statusCode(200).body("data", hasSize(0)).body("meta.total", is(3));
        as(op).get("/api/v1/operator/audit").then().statusCode(200).body("meta.size", is(20));

        // Acción: exacta, repetida y separada por comas
        as(op).get(base + "&action=REQUEST_PUBLISHED").then().body("meta.total", is(2))
                .body("data.action", everyItem(is("REQUEST_PUBLISHED")));
        as(op).get(base + "&action=REGISTER&action=REQUEST_PUBLISHED").then().body("meta.total", is(3));
        as(op).get(base + "&action=REGISTER,REQUEST_PUBLISHED").then().body("meta.total", is(3));
        as(op).get(base + "&action=NO_EXISTE").then().body("meta.total", is(0));

        // Entidad, id de entidad y actor
        as(op).get(base + "&entity=shipment_request").then().body("meta.total", is(2))
                .body("data.entityId", hasItems(r1, r2));
        as(op).get(base + "&entity=shipment_request&entityId=" + r1).then().body("meta.total", is(1))
                .body("data[0].entityId", is(r1)).body("data[0].entity", is("shipment_request"));
        as(op).get("/api/v1/operator/audit?entityId=" + r2).then().body("meta.total", is(1));
        as(op).get("/api/v1/operator/audit?actorId=" + actorId + "&size=100").then().body("meta.total", is(3))
                .body("data.actorProfileId", everyItem(is(actorId)));
        as(op).get("/api/v1/operator/audit?organizationId=" + other.orgId()).then().body("meta.total", is(2));

        // Fechas (hora de Lima, inclusive)
        as(op).get(base + "&from=" + today + "&to=" + today).then().body("meta.total", is(3));
        as(op).get(base + "&to=2000-01-01").then().body("meta.total", is(0));
        as(op).get(base + "&from=" + LocalDate.now(ZoneId.of("America/Lima")).plusDays(1)).then().body("meta.total", is(0));

        // Validación: LOGYX-VAL-001 con field
        for (String[] bad : new String[][]{
                {"size=0", "size"}, {"size=101", "size"}, {"page=-1", "page"}, {"actorId=zz", "actorId"},
                {"organizationId=zz", "organizationId"}, {"entityId=zz", "entityId"}, {"from=ayer", "from"},
                {"to=2030-13-45", "to"}, {"from=2030-01-02&to=2030-01-01", "from"},
                {"action=" + "A".repeat(61), "action"}, {"entity=" + "e".repeat(61), "entity"}}) {
            as(op).get("/api/v1/operator/audit?" + bad[0]).then().statusCode(400)
                    .body("error.code", is("LOGYX-VAL-001")).body("error.field", is(bad[1]));
        }
    }

    @Test
    void listaDeAccionesDistintas() {
        Session op = operator();
        Session pyme = register("PYME", "Audit Acciones");
        publish(pyme, 1000).statusCode(201);
        List<String> actions = as(op).get("/api/v1/operator/audit/actions").then().statusCode(200)
                .body("data", hasItems("REGISTER", "REQUEST_PUBLISHED", "OPERATOR_PROVISIONED"))
                .extract().path("data");
        assertEquals(actions.stream().distinct().sorted().toList(), actions, "sin repetidos y en orden alfabético");
    }

    @Test
    void soloElOperadorLeeLaAuditoria() {
        Session pyme = register("PYME", "Audit 403 Pyme");
        Session carrier = register("CARRIER", "Audit 403 Carrier");
        Session driver = Flows.awarded("Audit 403 Chofer").driver();
        for (String path : new String[]{"/api/v1/operator/audit", "/api/v1/operator/audit/actions"}) {
            as(pyme).get(path).then().statusCode(403);
            as(carrier).get(path).then().statusCode(403);
            as(driver).get(path).then().statusCode(403);
            io.restassured.RestAssured.given().get(path).then().statusCode(401);
        }
    }

    @Test
    void rlsLimitaAuditLogACadaOrganizacionYAbreloAlOperador() {
        Session op = operator();
        Session pyme = register("PYME", "Audit RLS Pyme");
        Session stranger = register("PYME", "Audit RLS Ajena");
        publish(pyme, 1000).statusCode(201);
        publish(stranger, 1000).statusCode(201);
        String mine = "SELECT count(*) FROM audit_log WHERE organization_id = '" + pyme.orgId() + "'";
        String everything = "SELECT count(*) FROM audit_log";
        assertEquals(2, countAs(pyme, "PYME", mine));
        // Otra organización no lee el rastro ajeno aunque la consulta no filtre
        assertEquals(0, countAs(stranger, "PYME", mine));
        assertEquals(2, countAs(stranger, "PYME", everything.replace("audit_log", "audit_log WHERE organization_id = '"
                + stranger.orgId() + "'")));
        assertTrue(countAs(stranger, "PYME", everything) < countAs(op, "OPERATOR", everything),
                "el operador ve más filas que una organización");
        assertEquals(2, countAs(op, "OPERATOR", mine));
        // El operador también lee perfiles y organizaciones para resolver al actor
        assertTrue(countAs(op, "OPERATOR", "SELECT count(*) FROM profiles") > 1);
    }
}
