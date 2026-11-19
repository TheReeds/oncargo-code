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

    /** Perfil (sub del JWT) de la sesión: lo fija {@code TenantSessionBinder} como {@code app.profile_id} (V16). */
    private static String profileOf(Session s) {
        String payload = s.token().split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("token sin sub");
        return m.group(1);
    }

    private long countAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                            + "set_config('app.profile_id', ?3, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
        });
    }

    /** Ejecuta un INSERT/UPDATE como logyx_app con el contexto de {@code s}; devuelve filas afectadas. */
    private int executeAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                            + "set_config('app.profile_id', ?3, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
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
    void soloElOperadorCambiaLaVerificacionDeUnaOrganizacion() {
        Session pyme = registerPending("PYME", "RLS Verif Pyme");
        Session other = registerPending("PYME", "RLS Verif Otra");
        Session op = operator();
        String self = "UPDATE organizations SET verification_status = 'VERIFIED' WHERE id = '" + pyme.orgId() + "'";
        // La propia organización puede editar su fila (orgs_update_own) pero no las columnas de verificación (trigger V13)
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME", self));
        assertEquals(1, executeAs(pyme, "PYME", "UPDATE organizations SET city = 'Puno' WHERE id = '" + pyme.orgId() + "'"));
        // Otra organización ni siquiera ve la fila para actualizarla
        assertEquals(0, executeAs(other, "PYME", "UPDATE organizations SET city = 'Lima' WHERE id = '" + pyme.orgId() + "'"));
        // El operador sí (orgs_operator_update)
        assertEquals(1, executeAs(op, "OPERATOR", self));
        assertEquals(1L, countAsOwner("SELECT count(*) FROM organizations WHERE id = '" + pyme.orgId()
                + "' AND verification_status = 'VERIFIED'"));
    }

    @Test
    void soloElOperadorActualizaEnviosYFlotaAjenos() {
        Flows.Awarded a = Flows.awarded("RLS Lifecycle");
        Session stranger = register("CARRIER", "RLS Lifecycle Ajeno");
        Session op = operator();
        String cancel = "UPDATE shipments SET status = 'CANCELLED' WHERE id = '" + a.shipmentId() + "'";
        assertEquals(0, executeAs(stranger, "CARRIER", cancel));
        assertEquals(0, executeAs(stranger, "CARRIER", "UPDATE vehicles SET status = 'MAINTENANCE' WHERE organization_id = '"
                + a.carrier().orgId() + "'"));
        assertEquals(1, executeAs(op, "OPERATOR", "UPDATE shipments SET cancel_reason = 'x' WHERE id = '" + a.shipmentId() + "'"));
        // HTTP: un tercero no cancela ni cambia el vehículo (404, no ve el envío)
        Api.as(stranger).body(Map.of("reason", "x")).post("/api/v1/shipments/" + a.shipmentId() + "/cancel")
                .then().statusCode(404);
        Api.as(stranger).body(Map.of("vehicleId", vehicle(stranger, plate(), 5000)))
                .put("/api/v1/shipments/" + a.shipmentId() + "/vehicle").then().statusCode(404);
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

        // V20 / RN02 por fecha: la agenda de ofertas (pendientes por vehículo) solo la ve su transportista
        String pendingOfVehicle = "SELECT count(*) FROM bids WHERE vehicle_id = '" + va + "' AND status = 'PENDING'";
        assertEquals(1, countAs(a, "CARRIER", pendingOfVehicle));
        assertEquals(0, countAs(b, "CARRIER", pendingOfVehicle), "B no ve las ofertas pendientes del vehículo de A");
        // y un transportista no archiva (ni edita) el vehículo de otro
        assertEquals(0, executeAs(b, "CARRIER", "UPDATE vehicles SET archived_at = now() WHERE id = '" + va + "'"));
        assertEquals(1, executeAs(a, "CARRIER", "UPDATE vehicles SET archived_at = NULL WHERE id = '" + va + "'"));
    }

    /** V15 (F15): quien ofertó sigue viendo la solicitud (solo lectura) aunque se adjudique a otro o se cancele. */
    @Test
    void ofertanteVeLaSolicitudPerdidaOCanceladaPeroNoLaAjenaNiEscribe() {
        Session pyme = register("PYME", "RLS F15 Pyme");
        Session winner = register("CARRIER", "RLS F15 Ganador");
        Session loser = register("CARRIER", "RLS F15 Perdedor");
        Session stranger = register("CARRIER", "RLS F15 Ajeno");
        String reqId = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        String cancelled = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        String vw = vehicle(winner, plate(), 5000), vl = vehicle(loser, plate(), 5000);
        String winBid = bid(winner, reqId, vw, 100);
        bid(loser, reqId, vl, 300);
        bid(loser, cancelled, vl, 300);
        Api.as(pyme).post("/api/v1/bids/" + winBid + "/accept").then().statusCode(200);
        Api.as(pyme).body(Map.of("reason", "x")).post("/api/v1/shipment-requests/" + cancelled + "/cancel")
                .then().statusCode(200);

        String ofRequest = "SELECT count(*) FROM shipment_requests WHERE id = '%s'";
        assertEquals(1, countAs(loser, "CARRIER", ofRequest.formatted(reqId)), "adjudicada a otro: sigue visible");
        assertEquals(1, countAs(loser, "CARRIER", ofRequest.formatted(cancelled)), "cancelada: sigue visible");
        assertEquals(1, countAs(winner, "CARRIER", ofRequest.formatted(reqId)));
        assertEquals(0, countAs(stranger, "CARRIER", ofRequest.formatted(reqId)), "sin oferta no hay lectura");
        assertEquals(0, countAs(stranger, "CARRIER", ofRequest.formatted(cancelled)));
        assertEquals(0, countAs(winner, "CARRIER", ofRequest.formatted(cancelled)), "el ganador no ofertó en la otra");
        // La política no abre las ofertas ajenas ni el envío del ganador
        assertEquals(1, countAs(loser, "CARRIER", "SELECT count(*) FROM bids WHERE request_id = '" + reqId + "'"));
        assertEquals(0, countAs(loser, "CARRIER", "SELECT count(*) FROM shipments WHERE request_id = '" + reqId + "'"));
        // Solo lectura
        assertEquals(0, executeAs(loser, "CARRIER", "UPDATE shipment_requests SET cancel_reason = 'x' WHERE id = '" + reqId + "'"));
        assertEquals(0, executeAs(loser, "CARRIER", "DELETE FROM shipment_requests WHERE id = '" + cancelled + "'"));
        // Y por HTTP el perdedor no ve datos del ganador
        Api.as(loser).get("/api/v1/shipment-requests/" + reqId).then().statusCode(200);
        Api.as(stranger).get("/api/v1/shipment-requests/" + reqId).then().statusCode(404);
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
                "INSERT INTO notifications (org_id, profile_id, type, title, body) VALUES ('" + a.pyme().orgId()
                        + "','" + profileOf(a.pyme()) + "','NEW_BID','x','y')"));
        assertTrue(rootMessage(ex3).contains("permission denied"), rootMessage(ex3));
    }

    @Inject
    pe.logyx.identity.application.AuthService accounts;

    /** V16: avisos y preferencias por persona. Un compañero de la misma organización no ve ni marca los avisos de otro. */
    @Test
    void avisosYPreferenciasSonDeCadaPersonaDentroDeLaOrganizacion() {
        Flows.Bidding b = Flows.bidding("RLS Persona");
        String email = "rls.ops" + System.nanoTime() + "@test.pe";
        accounts.provisionUser(java.util.UUID.fromString(b.pyme().orgId()), email, "Miembro1234", "RLS OPS",
                pe.logyx.identity.domain.model.Profile.Role.OPS);
        Session colleague = login(email, "Miembro1234");
        // Un aviso nuevo para la organización: una copia por persona
        Session carrier2 = register("CARRIER", "RLS Persona C2");
        bid(carrier2, b.requestId(), vehicle(carrier2, plate(), 8000), 350);

        String mine = "SELECT count(*) FROM notifications WHERE profile_id = '" + profileOf(b.pyme()) + "'";
        String theirs = "SELECT count(*) FROM notifications WHERE profile_id = '" + profileOf(colleague) + "'";
        String org = "SELECT count(*) FROM notifications WHERE org_id = '" + b.pyme().orgId() + "'";
        long own = countAs(b.pyme(), "PYME", mine);
        assertTrue(own >= 2, "el admin tiene sus avisos");
        assertEquals(own, countAs(b.pyme(), "PYME", org), "sin filtrar por persona, RLS solo devuelve los propios");
        assertEquals(0, countAs(b.pyme(), "PYME", theirs), "no ve las copias de su compañero");
        assertEquals(1, countAs(colleague, "PYME", theirs));
        assertEquals(own + 1, countAsOwner(org));
        // Marcar como leído el aviso de un compañero: 0 filas; el propio sí
        assertEquals(0, executeAs(b.pyme(), "PYME", "UPDATE notifications SET read_at = now() WHERE profile_id = '"
                + profileOf(colleague) + "'"));
        assertEquals(1, countAsOwner(theirs + " AND read_at IS NULL"));
        assertEquals(1, executeAs(colleague, "PYME", "UPDATE notifications SET read_at = now() WHERE profile_id = '"
                + profileOf(colleague) + "'"));
        // Solo read_at es editable (privilegio por columna)
        var ex = assertThrows(Exception.class, () -> executeAs(colleague, "PYME",
                "UPDATE notifications SET title = 'x', profile_id = '" + profileOf(b.pyme()) + "' WHERE profile_id = '"
                        + profileOf(colleague) + "'"));
        assertTrue(rootMessage(ex).contains("permission denied"), rootMessage(ex));
        // Sin perfil en la sesión (fail-closed): nada
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', 'PYME', true)")
                    .setParameter(1, b.pyme().orgId()).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return ((Number) em.createNativeQuery(org).getSingleResult()).longValue();
        }));

        // Preferencias: cada persona las suyas; no se escriben a nombre de otro
        assertEquals(1, executeAs(colleague, "PYME", "INSERT INTO notification_preferences (profile_id, org_id, category, "
                + "in_app, email) VALUES ('" + profileOf(colleague) + "','" + b.pyme().orgId() + "','BIDS',false,false)"));
        String prefs = "SELECT count(*) FROM notification_preferences WHERE org_id = '" + b.pyme().orgId() + "'";
        assertEquals(1, countAs(colleague, "PYME", prefs));
        assertEquals(0, countAs(b.pyme(), "PYME", prefs), "el admin no lee las preferencias de su compañero");
        var ex2 = assertThrows(Exception.class, () -> executeAs(b.pyme(), "PYME", "INSERT INTO notification_preferences "
                + "(profile_id, org_id, category, in_app, email) VALUES ('" + profileOf(colleague) + "','"
                + b.pyme().orgId() + "','TRIPS',false,false)"));
        assertTrue(rootMessage(ex2).contains("row-level security"), rootMessage(ex2));
        assertEquals(0, executeAs(b.pyme(), "PYME", "UPDATE notification_preferences SET in_app = true WHERE org_id = '"
                + b.pyme().orgId() + "'"));
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

    @Test
    void eventosEnTiempoRealSoloLosLeeSuDestinatarioYNadieLosEscribe() {
        Session pymeA = register("PYME", "RLS RT Pyme A");
        Session pymeB = register("PYME", "RLS RT Pyme B");
        Session carrier = register("CARRIER", "RLS RT Carrier");
        String tag = "rls-" + System.nanoTime();
        systemTx.run(() -> {
            em.createNativeQuery("INSERT INTO realtime_events (event_type, org_id, payload) VALUES ('bid.placed', ?1, "
                    + "CAST(?2 AS jsonb))").setParameter(1, java.util.UUID.fromString(pymeA.orgId()))
                    .setParameter(2, "{\"tag\":\"" + tag + "\"}").executeUpdate();
            em.createNativeQuery("INSERT INTO realtime_events (event_type, org_type, payload) VALUES ('auction.updated', "
                    + "'CARRIER', CAST(?1 AS jsonb))").setParameter(1, "{\"tag\":\"" + tag + "\"}").executeUpdate();
        });
        String q = "SELECT count(*) FROM realtime_events WHERE payload->>'tag' = '" + tag + "'";
        assertEquals(1, countAs(pymeA, "PYME", q), "la PYME ve solo lo dirigido a ella");
        assertEquals(0, countAs(pymeB, "PYME", q), "otra PYME no ve nada");
        assertEquals(1, countAs(carrier, "CARRIER", q), "un transportista ve solo la difusión a transportistas");
        // logyx_app no inserta, modifica ni borra la proyección
        for (String sql : java.util.List.of(
                "INSERT INTO realtime_events (event_type, org_id, payload) VALUES ('x', '" + pymeB.orgId() + "', '{}')",
                "UPDATE realtime_events SET event_type = 'x' WHERE payload->>'tag' = '" + tag + "'",
                "DELETE FROM realtime_events WHERE payload->>'tag' = '" + tag + "'")) {
            var ex = assertThrows(Exception.class, () -> executeAs(pymeA, "PYME", sql), sql);
            assertTrue(rootMessage(ex).contains("permission denied"), rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }

    @Test
    void logyxAppNoTocaLasTablasDeTokens() {
        Session pyme = register("PYME", "RLS Tokens");
        // Genera filas: un refresh token y un token de restablecimiento
        io.restassured.RestAssured.given().contentType("application/json")
                .body(Map.of("email", pyme.email(), "password", "Secreta123", "refresh", true))
                .post("/api/v1/auth/login").then().statusCode(200);
        assertTrue(countAsOwner("SELECT count(*) FROM refresh_tokens WHERE profile_id = (SELECT id FROM profiles "
                + "WHERE email = '" + pyme.email() + "')") >= 1);
        for (String table : java.util.List.of("password_tokens", "refresh_tokens")) {
            // Ni con la organización del dueño de los tokens: sin privilegios (42501), RLS sin políticas detrás
            assertThrows(Exception.class, () -> countAs(pyme, "PYME", "SELECT count(*) FROM " + table), table);
            assertThrows(Exception.class, () -> executeAs(pyme, "PYME", "DELETE FROM " + table), table);
        }
        assertEquals(1L, countAsOwner("SELECT count(*) FROM pg_class WHERE relname = 'refresh_tokens' AND relrowsecurity"));
    }

    @Test
    void unUsuarioNoCambiaPerfilesDeOtraOrganizacion() {
        Session a = register("PYME", "RLS Perfiles A");
        Session b = register("PYME", "RLS Perfiles B");
        assertEquals(0, executeAs(a, "PYME", "UPDATE profiles SET role = 'VIEWER', session_version = session_version + 1 "
                + "WHERE organization_id = '" + b.orgId() + "'"));
        assertEquals(0L, countAs(a, "PYME", "SELECT count(*) FROM profiles WHERE organization_id = '" + b.orgId() + "'"));
    }

    // ----- V18: parámetros de sistema, historial y aceptación de términos

    @Test
    void soloElOperadorEscribeParametrosDeSistemaYElHistorialSoloSeInserta() {
        Session pyme = register("PYME", "RLS Params Pyme");
        Session carrier = register("CARRIER", "RLS Params Carrier");
        Session op = operator();
        String insert = "INSERT INTO system_parameters (param_key, param_value, version, reason) "
                + "VALUES ('commission_rate', '0.09', 1, 'rls')";
        // Cualquier organización autenticada lee (sin fila no hay nada que ver), pero no escribe
        assertEquals(0L, countAs(pyme, "PYME", "SELECT count(*) FROM system_parameters WHERE param_key = 'zzz'"));
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME", insert));
        assertThrows(Exception.class, () -> executeAs(carrier, "CARRIER", insert));
        assertEquals(0, executeAs(pyme, "PYME", "UPDATE system_parameters SET param_value = '0'"));
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME", "DELETE FROM system_parameters"));
        // El historial: solo lectura e inserción del operador, y a su propio nombre
        assertEquals(0L, countAs(pyme, "PYME", "SELECT count(*) FROM system_parameter_history"));
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME", "INSERT INTO system_parameter_history "
                + "(param_key, version, new_value, reason, changed_by_org) VALUES ('commission_rate', 1, '0.09', 'x', '"
                + pyme.orgId() + "')"));
        assertThrows(Exception.class, () -> executeAs(op, "OPERATOR", "INSERT INTO system_parameter_history "
                + "(param_key, version, new_value, reason, changed_by_org) VALUES ('commission_rate', 1, '0.09', 'x', '"
                + pyme.orgId() + "')"));   // changed_by_org distinto de la del operador
        assertEquals(1, executeAs(op, "OPERATOR", "INSERT INTO system_parameter_history "
                + "(param_key, version, new_value, reason, changed_by_org) VALUES ('rls_test_key', 1, '1', 'x', '"
                + op.orgId() + "')"));
        assertThrows(Exception.class, () -> executeAs(op, "OPERATOR", "UPDATE system_parameter_history SET reason = 'y'"));
        assertThrows(Exception.class, () -> executeAs(op, "OPERATOR", "DELETE FROM system_parameter_history"));
        assertEquals(1L, countAs(op, "OPERATOR", "SELECT count(*) FROM system_parameter_history WHERE param_key = 'rls_test_key'"));
        assertEquals(0L, countAs(pyme, "PYME", "SELECT count(*) FROM system_parameter_history WHERE param_key = 'rls_test_key'"));
        systemTx.run(() -> em.createNativeQuery("DELETE FROM system_parameter_history WHERE param_key = 'rls_test_key'").executeUpdate());
    }

    @Test
    void cadaOrganizacionVeSusAceptacionesDeTerminosYNadieLasEscribe() {
        Session a = register("PYME", "RLS Terminos A");
        Session b = register("PYME", "RLS Terminos B");
        Session op = operator();
        assertEquals(1L, countAs(a, "PYME", "SELECT count(*) FROM terms_acceptances"));
        assertEquals(0L, countAs(a, "PYME", "SELECT count(*) FROM terms_acceptances WHERE organization_id = '" + b.orgId() + "'"));
        assertTrue(countAs(op, "OPERATOR", "SELECT count(*) FROM terms_acceptances") >= 2);
        // Es evidencia: ni insertar (el registro corre sin usuario, por SystemTx), ni cambiar, ni borrar
        assertThrows(Exception.class, () -> executeAs(a, "PYME", "INSERT INTO terms_acceptances (id, profile_id, organization_id, "
                + "terms_version) VALUES (gen_random_uuid(), (SELECT id FROM profiles WHERE organization_id = '" + a.orgId()
                + "' LIMIT 1), '" + a.orgId() + "', 'falsa')"));
        assertThrows(Exception.class, () -> executeAs(a, "PYME", "UPDATE terms_acceptances SET terms_version = 'x'"));
        assertThrows(Exception.class, () -> executeAs(a, "PYME", "DELETE FROM terms_acceptances"));
        assertThrows(Exception.class, () -> executeAs(op, "OPERATOR", "DELETE FROM terms_acceptances"));
    }

    // ----- V19 (Ola 1 zona B): paradas por conductor y enlaces públicos de seguimiento

    @Test
    void unConductorSoloVeYTocaLasParadasDeSusEnvios() {
        Flows.Awarded a = Flows.awarded("RLS Paradas");
        String[] other = driver(a.carrier(), "Otro conductor RLS");
        Session otherDriver = login(other[1], "Chofer1234");
        String stops = "SELECT count(*) FROM shipment_stops WHERE shipment_id = '" + a.shipmentId() + "'";
        assertEquals(2L, countAs(a.driver(), "CARRIER", stops));
        assertEquals(2L, countAs(a.carrier(), "CARRIER", stops), "la oficina del transportista sigue viendo todo");
        assertEquals(0L, countAs(otherDriver, "CARRIER", stops), "otro conductor de la misma flota no");
        assertEquals(0L, countAs(otherDriver, "CARRIER", "SELECT count(*) FROM shipment_stops"));
        assertEquals(0, executeAs(otherDriver, "CARRIER", "UPDATE shipment_stops SET status = 'ARRIVED' "
                + "WHERE shipment_id = '" + a.shipmentId() + "'"));
        assertEquals(2L, countAs(a.pyme(), "PYME", stops));
    }

    @Test
    void enlacesPublicosSonDeLaPymeDuenaYSoloSeRevocan() {
        Flows.Awarded a = Flows.awarded("RLS Enlaces");
        Session otherPyme = register("PYME", "RLS Enlaces otra");
        Session op = operator();
        as(a.pyme()).post("/api/v1/shipments/" + a.shipmentId() + "/share-links").then().statusCode(201);
        String all = "SELECT count(*) FROM shipment_share_links WHERE shipment_id = '" + a.shipmentId() + "'";
        assertEquals(1L, countAs(a.pyme(), "PYME", all));
        assertEquals(0L, countAs(otherPyme, "PYME", all));
        assertEquals(0L, countAs(a.carrier(), "CARRIER", all), "el transportista no ve los enlaces de la PYME");
        assertEquals(0L, countAs(a.driver(), "CARRIER", all));
        assertEquals(1L, countAs(op, "OPERATOR", all));
        // Ni otra PYME revoca, ni la dueña cambia otra cosa que la revocación, ni nadie borra
        assertEquals(0, executeAs(otherPyme, "PYME", "UPDATE shipment_share_links SET revoked_at = now()"));
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME", "UPDATE shipment_share_links SET views = 999"));
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME",
                "UPDATE shipment_share_links SET token_hash = repeat('0', 64)"));
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME", "DELETE FROM shipment_share_links"));
        assertEquals(1, executeAs(a.pyme(), "PYME", "UPDATE shipment_share_links SET revoked_at = now() "
                + "WHERE shipment_id = '" + a.shipmentId() + "'"));
        // Nadie crea un enlace para un envío ajeno (WITH CHECK: el envío debe ser de su organización)
        assertThrows(Exception.class, () -> executeAs(otherPyme, "PYME", "INSERT INTO shipment_share_links "
                + "(id, shipment_id, requester_org_id, token_hash, token_hint) VALUES (gen_random_uuid(), '"
                + a.shipmentId() + "', '" + otherPyme.orgId() + "', repeat('a', 64), 'abcd')"));
        assertThrows(Exception.class, () -> executeAs(a.carrier(), "CARRIER", "INSERT INTO shipment_share_links "
                + "(id, shipment_id, requester_org_id, token_hash, token_hint) VALUES (gen_random_uuid(), '"
                + a.shipmentId() + "', '" + a.carrier().orgId() + "', repeat('b', 64), 'abcd')"));
    }
}
