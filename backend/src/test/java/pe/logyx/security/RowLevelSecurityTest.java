package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.shared.tenancy.SystemTx;
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

    @Inject
    SystemTx systemTx;

    /** Datasource privilegiado (dueño del esquema): sin RLS. */
    private long countAsOwner(String sql) {
        return systemTx.call(() ->
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
        // Fuera de SystemTx la conexión es la del usuario de la app (ADR-0006), nunca la del dueño.
        String role = QuarkusTransaction.requiringNew().call(() ->
                (String) em.createNativeQuery("SELECT current_user").getSingleResult());
        assertEquals("logyx_api", role, "sin SystemTx se usa el usuario de la aplicación");
        String system = systemTx.call(() -> (String) em.createNativeQuery("SELECT current_user").getSingleResult());
        assertNotEquals("logyx_api", system, "SystemTx usa el datasource privilegiado");
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

    @Test
    void viajesReservasYSugerenciasSmartLoadRespetanRls() {
        Session carrierA = register("CARRIER", "RLS Trip A");
        Session carrierB = register("CARRIER", "RLS Trip B");
        Session pymeA = register("PYME", "RLS Trip PymeA");
        Session pymeB = register("PYME", "RLS Trip PymeB");
        Session op = operator();
        String vehicleA = vehicle(carrierA, plate(), 8000);
        String trip = publishTrip(carrierA, vehicleA, 8000, "PER_KG", 0.5, 3).statusCode(201).extract().path("data.id");
        String booking = bookTrip(pymeA, trip, 1000).statusCode(201).extract().path("data.id");

        String trips = "SELECT count(*) FROM carrier_trips WHERE id = '" + trip + "'";
        String bookings = "SELECT count(*) FROM trip_bookings WHERE id = '" + booking + "'";
        // Viaje abierto: lo ven su transportista, las PYMEs y el operador; otro transportista no
        assertEquals(1, countAs(carrierA, "CARRIER", trips));
        assertEquals(0, countAs(carrierB, "CARRIER", trips), "otro transportista no ve el viaje");
        assertEquals(1, countAs(pymeB, "PYME", trips), "listado público para PYMEs");
        assertEquals(1, countAs(op, "OPERATOR", trips));
        // Reserva: solo las dos partes y el operador
        assertEquals(1, countAs(pymeA, "PYME", bookings));
        assertEquals(1, countAs(carrierA, "CARRIER", bookings));
        assertEquals(0, countAs(pymeB, "PYME", bookings), "otra PYME no ve la reserva");
        assertEquals(0, countAs(carrierB, "CARRIER", bookings));
        assertEquals(1, countAs(op, "OPERATOR", bookings));

        // Cerrado: deja de ser público, pero la PYME que reservó lo sigue viendo
        Api.as(carrierA).post("/api/v1/trips/" + trip + "/close").then().statusCode(200);
        assertEquals(0, countAs(pymeB, "PYME", trips));
        assertEquals(1, countAs(pymeA, "PYME", trips), "quien reservó lo sigue viendo");

        // Escrituras: la PYME no crea ni edita viajes; nadie reserva en nombre de otra PYME
        var ex = assertThrows(Exception.class, () -> executeAs(pymeA, "PYME",
                "INSERT INTO carrier_trips (carrier_org_id, vehicle_id, vehicle_type, vehicle_plate, origin_city, "
                        + "destination_city, departure_at, capacity_kg, pricing_mode, rate) VALUES ('" + pymeA.orgId()
                        + "','" + vehicleA + "','VAN','AAA-111','LIMA','CUSCO', now(), 100, 'PER_KG', 1)"));
        assertTrue(rootMessage(ex).contains("row-level security"), rootMessage(ex));
        assertEquals(0, executeAs(pymeA, "PYME", "UPDATE carrier_trips SET capacity_kg = 1 WHERE id = '" + trip + "'"));
        assertEquals(0, executeAs(carrierB, "CARRIER", "UPDATE trip_bookings SET status = 'CONFIRMED' WHERE id = '"
                + booking + "'"));
        String requestedBy = systemTx.call(() -> String.valueOf(em.createNativeQuery(
                "SELECT requested_by FROM trip_bookings WHERE id = ?1").setParameter(1, java.util.UUID.fromString(booking))
                .getSingleResult()));
        var ex2 = assertThrows(Exception.class, () -> executeAs(pymeB, "PYME",
                "INSERT INTO trip_bookings (trip_id, requester_org_id, carrier_org_id, requested_by, origin_city, "
                        + "destination_city, departure_at, weight_kg, cargo_type, cargo_description, pickup_address, "
                        + "delivery_address, price) VALUES ('" + trip + "','" + pymeA.orgId() + "','" + carrierA.orgId()
                        + "','" + requestedBy + "','LIMA','JULIACA', now(), 10, 'GENERAL', 'x', 'a', 'b', 5)"));
        assertTrue(rootMessage(ex2).contains("row-level security"), rootMessage(ex2));

        // Smart Load: sugerencias e ítems privados del transportista (el operador las lee, RF59)
        java.util.UUID suggestion = java.util.UUID.randomUUID();
        systemTx.run(() -> em.createNativeQuery(
                "INSERT INTO smart_load_suggestions (id, carrier_org_id, vehicle_id, vehicle_type, vehicle_plate, "
                        + "capacity_kg, total_weight_kg, combined_income, occupancy_pct, expires_at) VALUES (?1, ?2, ?3, "
                        + "'TRUCK_MEDIUM', 'AAA-111', 8000, 5000, 4000, 62.5, now() + interval '4 hours')")
                .setParameter(1, suggestion).setParameter(2, java.util.UUID.fromString(carrierA.orgId()))
                .setParameter(3, java.util.UUID.fromString(vehicleA)).executeUpdate());
        String smart = "SELECT count(*) FROM smart_load_suggestions WHERE id = '" + suggestion + "'";
        assertEquals(1, countAs(carrierA, "CARRIER", smart));
        assertEquals(0, countAs(carrierB, "CARRIER", smart));
        assertEquals(0, countAs(pymeA, "PYME", smart));
        assertEquals(1, countAs(op, "OPERATOR", smart));
        assertEquals(0, executeAs(carrierB, "CARRIER", "UPDATE smart_load_suggestions SET status = 'ACCEPTED' WHERE id = '"
                + suggestion + "'"));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}
