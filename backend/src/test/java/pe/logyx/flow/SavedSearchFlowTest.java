package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.support.Api.Session;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;


/** Roadmap 2.3: búsquedas guardadas por persona (RLS) y avisos NEW_LOAD_MATCH al publicar una carga que coincide. */
@QuarkusTest
class SavedSearchFlowTest {

    private static final String SEARCHES = "/api/v1/saved-searches";

    @Inject AuthService auth;

    /** Las solicitudes publicadas quedan abiertas días y otros tests (Smart Load, listados) las verían: se cancelan al terminar. */
    private final List<Map.Entry<Session, String>> published = new java.util.ArrayList<>();

    private io.restassured.response.ValidatableResponse publishRoute(Session pyme, String from, String to, int kg, int days) {
        var r = pe.logyx.support.Api.publishRoute(pyme, from, to, kg, days);
        String id = r.extract().path("data.id");
        if (id != null) published.add(Map.entry(pyme, id));
        return r;
    }

    @AfterEach
    void cancelPublished() {
        published.forEach(e -> as(e.getKey()).body(Map.of()).post("/api/v1/shipment-requests/" + e.getValue() + "/cancel"));
        published.clear();
    }

    private Session member(Session admin, Profile.Role role) {
        String email = role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Persona " + role, role);
        return login(email, "Miembro1234");
    }

    private static int unread(Session s) {
        return as(s).get("/api/v1/notifications/unread-count").then().statusCode(200).extract().path("data.unread");
    }

    private static List<String> matchTypes(Session s) {
        return as(s).get("/api/v1/notifications?size=50").then().statusCode(200).extract().path("data.type");
    }

    private static String save(Session s, Map<String, Object> body) {
        return as(s).body(body).post(SEARCHES).then().statusCode(201).extract().path("data.id");
    }

    @Test
    void avisaSoloCuandoLaCargaCoincideYElVehiculoPuedeOfertar() {
        Session carrier = register("CARRIER", "Alertas Carrier");
        vehicle(carrier, plate(), 8000);
        save(carrier, Map.of("name", "Lima a Juliaca", "originCity", "LIMA", "destinationCity", "JULIACA"));
        Session pyme = register("PYME", "Alertas Pyme");

        String match = publishRoute(pyme, "LIMA", "JULIACA", 1500, 20).statusCode(201).extract().path("data.id");
        assertEquals(1, unread(carrier));
        as(carrier).get("/api/v1/notifications").then().statusCode(200)
                .body("data[0].type", is("NEW_LOAD_MATCH")).body("data[0].relatedType", is("REQUEST"))
                .body("data[0].relatedId", is(match)).body("data[0].title", containsString("Lima"))
                .body("data[0].body", containsString("Lima a Juliaca"));

        publishRoute(pyme, "LIMA", "PUNO", 1500, 20).statusCode(201);                // otra ruta: nada
        publishRoute(pyme, "ICA", "JULIACA", 1500, 20).statusCode(201);              // otro origen: nada
        publishRoute(pyme, "LIMA", "JULIACA", 15000, 20).statusCode(201);           // no cabe en la flota (8 t): nada
        assertEquals(1, unread(carrier));
    }

    @Test
    void elFiltroDeVehiculoYLaPausaSeRespetan() {
        Session carrier = register("CARRIER", "Alertas Veh");
        String small = vehicleOf(carrier, "VAN", 600, null);
        vehicle(carrier, plate(), 8000);
        save(carrier, Map.of("name", "Solo la van", "originCity", "LIMA", "vehicleId", small));
        String pausedId = save(carrier, Map.of("name", "Pausada", "originCity", "LIMA", "cargoType", "GENERAL"));
        as(carrier).body(Map.of("name", "Pausada", "originCity", "LIMA", "cargoType", "GENERAL", "active", false))
                .put(SEARCHES + "/" + pausedId).then().statusCode(200).body("data.active", is(false));
        Session pyme = register("PYME", "Alertas Veh Pyme");

        publishRoute(pyme, "LIMA", "TACNA", 1500, 20).statusCode(201);     // 1,5 t no cabe en la van; la otra está pausada
        assertEquals(0, unread(carrier));
        publishRoute(pyme, "LIMA", "TACNA", 400, 20).statusCode(201);      // 400 kg sí cabe en la van
        assertEquals(1, unread(carrier));
        assertEquals(List.of("NEW_LOAD_MATCH"), matchTypes(carrier));
    }

    @Test
    void sinVehiculosNoHayAvisoYUnaPersonaNoVeLasBusquedasDeOtra() {
        Session admin = register("CARRIER", "Alertas Equipo");
        Session ops = member(admin, Profile.Role.OPS);
        String mine = save(admin, Map.of("name", "Mía", "originCity", "LIMA"));
        as(ops).get(SEARCHES).then().statusCode(200).body("data", hasSize(0));       // RLS por persona
        as(ops).body(Map.of("name", "Robada", "originCity", "LIMA")).put(SEARCHES + "/" + mine).then().statusCode(404);
        as(ops).delete(SEARCHES + "/" + mine).then().statusCode(404);
        as(admin).get(SEARCHES).then().body("data", hasSize(1)).body("data[0].name", is("Mía"));

        // Sin flota no hay a qué ofertar: no se avisa
        Session pyme = register("PYME", "Alertas Equipo Pyme");
        publishRoute(pyme, "LIMA", "TACNA", 1000, 20).statusCode(201);
        assertEquals(0, unread(admin));
        // Con un vehículo sí, y solo a quien guardó la búsqueda
        vehicle(admin, plate(), 8000);
        publishRoute(pyme, "LIMA", "TACNA", 1000, 20).statusCode(201);
        assertEquals(1, unread(admin));
        assertEquals(0, unread(ops));
        as(admin).delete(SEARCHES + "/" + mine).then().statusCode(200);
        as(admin).get(SEARCHES).then().body("data", hasSize(0));
    }

    @Test
    void validaFiltrosVehiculoAjenoYTope() {
        Session carrier = register("CARRIER", "Alertas Valida");
        as(carrier).body(Map.of("name", "Sin filtros")).post(SEARCHES).then().statusCode(400)
                .body("error.code", is("LOGYX-ALT-001"));
        as(carrier).body(Map.of("name", "Misma ciudad", "originCity", "LIMA", "destinationCity", "LIMA")).post(SEARCHES)
                .then().statusCode(400).body("error.code", is("LOGYX-ALT-003"));
        as(carrier).body(Map.of("name", "Fechas", "originCity", "LIMA", "pickupFrom", "2026-12-20", "pickupTo", "2026-12-10"))
                .post(SEARCHES).then().statusCode(400).body("error.code", is("LOGYX-ALT-003"));
        Session other = register("CARRIER", "Alertas Otro");
        String foreign = vehicle(other, plate(), 8000);
        as(carrier).body(Map.of("name", "Ajeno", "originCity", "LIMA", "vehicleId", foreign)).post(SEARCHES)
                .then().statusCode(404);
        as(register("PYME", "Alertas No Carrier")).get(SEARCHES).then().statusCode(403);
        for (int i = 0; i < 20; i++) save(carrier, Map.of("name", "B" + i, "originCity", "LIMA"));
        as(carrier).body(Map.of("name", "Una más", "originCity", "LIMA")).post(SEARCHES).then().statusCode(409)
                .body("error.code", is("LOGYX-ALT-002"));
    }

    @Test
    void elLimitePorHoraCorta() {
        Session carrier = register("CARRIER", "Alertas Tope");
        vehicle(carrier, plate(), 8000);
        save(carrier, Map.of("name", "Todo a Tacna", "destinationCity", "TACNA"));
        Session pyme = register("PYME", "Alertas Tope Pyme");
        for (int i = 0; i < 12; i++) publishRoute(pyme, "LIMA", "TACNA", 500, 20).statusCode(201);
        assertEquals(10, unread(carrier), "máximo 10 avisos por búsqueda y hora");
    }

    @Test
    void unTransportistaBloqueadoPorLaPymeNoRecibeAvisosDeSusCargas() {
        Session carrier = register("CARRIER", "Alertas Bloqueado");
        vehicle(carrier, plate(), 8000);
        save(carrier, Map.of("name", "Lima a Ica", "originCity", "LIMA", "destinationCity", "ICA"));
        Session pyme = register("PYME", "Alertas Bloquea");
        Session otra = register("PYME", "Alertas Otra Pyme");
        as(pyme).body(Map.of("kind", "BLOCKED")).put("/api/v1/carrier-preferences/" + carrier.orgId()).then().statusCode(200);
        publishRoute(pyme, "LIMA", "ICA", 1000, 20).statusCode(201);
        assertEquals(0, unread(carrier), "la PYME que lo bloqueó no le genera avisos");
        publishRoute(otra, "LIMA", "ICA", 1000, 20).statusCode(201);
        assertEquals(1, unread(carrier), "las demás PYMEs sí");
    }
}
