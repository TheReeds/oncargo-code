package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * V17 directamente en PostgreSQL con consultas que NO filtran por organización: libreta de direcciones, claves de
 * idempotencia y la columna de contactos de la solicitud (viaja en filas ya protegidas).
 */
@QuarkusTest
class RequestDetailsRlsTest {

    @Inject EntityManager em;

    private long countAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
        });
    }

    private int executeAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return em.createNativeQuery(sql).executeUpdate();
        });
    }

    private static Map<String, Object> place(String label) {
        return Map.of("label", label, "city", "LIMA", "address", "Av. Industrial 450", "contactPhone", "987654321");
    }

    @Test
    void laLibretaDeDireccionesEsDeCadaOrganizacion() {
        Session a = register("PYME", "RLS Places A");
        Session b = register("PYME", "RLS Places B");
        String id = as(a).body(place("Privada")).post("/api/v1/places").then().statusCode(201).extract().path("data.id");
        as(b).body(place("Otra")).post("/api/v1/places").then().statusCode(201);

        assertEquals(1L, countAs(a, "PYME", "SELECT count(*) FROM saved_places"));
        assertEquals(0L, countAs(b, "PYME", "SELECT count(*) FROM saved_places WHERE id = '" + id + "'"));
        // Ni actualiza, ni borra, ni inserta a nombre de otra organización
        assertEquals(0, executeAs(b, "PYME", "UPDATE saved_places SET label = 'x' WHERE id = '" + id + "'"));
        assertEquals(0, executeAs(b, "PYME", "DELETE FROM saved_places WHERE id = '" + id + "'"));
        assertEquals(0, executeAs(a, "PYME", "DELETE FROM saved_places WHERE id = '" + id + "'"),
                "sin política de DELETE: ni la dueña borra (se archiva)");
        assertThrows(Exception.class, () -> executeAs(b, "PYME", "INSERT INTO saved_places (organization_id, label, city, "
                + "address, created_by) SELECT '" + a.orgId() + "', 'x', 'LIMA', 'y', id FROM profiles LIMIT 1"));
    }

    @Test
    void lasClavesDeIdempotenciaSonDeCadaOrganizacion() {
        Session a = register("PYME", "RLS Idem A");
        Session b = register("PYME", "RLS Idem B");
        String key = "rls-key-" + System.nanoTime();
        as(a).header("Idempotency-Key", key).body(Map.of("originCity", "LIMA", "originAddress", "Av. A 1",
                "destinationCity", "JULIACA", "destinationAddress", "Jr. B 2", "cargoType", "GENERAL",
                "cargoDescription", "Cajas", "weightKg", 500, "pickupDate", java.time.LocalDate.now().plusDays(2).toString()))
                .post("/api/v1/shipment-requests").then().statusCode(201);
        assertEquals(1L, countAs(a, "PYME", "SELECT count(*) FROM idempotency_keys WHERE key = '" + key + "'"));
        assertEquals(0L, countAs(b, "PYME", "SELECT count(*) FROM idempotency_keys WHERE key = '" + key + "'"));
        assertEquals(0, executeAs(b, "PYME", "DELETE FROM idempotency_keys WHERE key = '" + key + "'"));
        assertThrows(Exception.class, () -> executeAs(b, "PYME", "INSERT INTO idempotency_keys (organization_id, scope, key, "
                + "resource_id) VALUES ('" + a.orgId() + "', 'shipment_request', 'otra', gen_random_uuid())"));
    }

    @Test
    void elCodigoEsUnicoYObligatorio() {
        Session a = register("PYME", "RLS Codigo");
        var req = Map.of("originCity", "LIMA", "originAddress", "Av. A 1", "destinationCity", "JULIACA",
                "destinationAddress", "Jr. B 2", "cargoType", "GENERAL", "cargoDescription", "Cajas", "weightKg", 500,
                "pickupDate", java.time.LocalDate.now().plusDays(2).toString());
        String first = as(a).body(req).post("/api/v1/shipment-requests").then().statusCode(201).extract().path("data.id");
        String second = as(a).body(req).post("/api/v1/shipment-requests").then().statusCode(201).extract().path("data.id");
        String code = as(a).get("/api/v1/shipment-requests/" + first).path("data.code");
        var system = io.quarkus.arc.Arc.container().instance(pe.logyx.shared.tenancy.SystemTx.class).get();
        // Como dueño del esquema: ni repetir un código ni dejarlo vacío
        assertThrows(Exception.class, () -> system.run(() -> em.createNativeQuery(
                "UPDATE shipment_requests SET code = '" + code + "' WHERE id = '" + second + "'").executeUpdate()));
        assertThrows(Exception.class, () -> system.run(() -> em.createNativeQuery(
                "UPDATE shipment_requests SET code = NULL WHERE id = '" + second + "'").executeUpdate()));
    }
}
