package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Roadmap 2.2 (V24): preferencias de la organización (el ADMIN escribe, todos leen, el operador lee) y alertas
 * implícitas por las rutas de interés del transportista (2.3): misma regla de vehículo, sin duplicar con una búsqueda
 * guardada, sin bloqueados, idempotentes y con tope por organización y hora.
 */
@QuarkusTest
class OrganizationPreferencesFlowTest {

    private static final String PREFS = "/api/v1/organizations/me/preferences";

    @Inject AuthService auth;

    private final List<Map.Entry<Session, String>> published = new ArrayList<>();

    private String publish(Session pyme, String from, String to, int kg) {
        String id = Api.publishRoute(pyme, from, to, kg, 20).statusCode(201).extract().path("data.id");
        published.add(Map.entry(pyme, id));
        return id;
    }

    @AfterEach
    void cancelPublished() {
        published.forEach(e -> as(e.getKey()).body(Map.of()).post("/api/v1/shipment-requests/" + e.getValue() + "/cancel"));
        published.clear();
    }

    private Session member(Session admin, Profile.Role role) {
        String email = "orgpref" + role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Persona " + role, role);
        return login(email, "Miembro1234");
    }

    private static Map<String, Object> carrierPrefs(List<Map<String, String>> routes, Object minPerKm, boolean alerts,
                                                    List<String> cargoTypes) {
        Map<String, Object> m = new HashMap<>();
        m.put("cities", List.of("LIMA", "JULIACA"));
        m.put("routes", routes);
        m.put("cargoTypes", cargoTypes);
        m.put("routeAlerts", alerts);
        m.put("minPricePerKm", minPerKm);
        return m;
    }

    private static Map<String, String> route(String o, String d) {
        return Map.of("origin", o, "destination", d);
    }

    private static long matches(Session s) {
        List<String> types = as(s).get("/api/v1/notifications?size=100").then().statusCode(200).extract().path("data.type");
        return types.stream().filter("NEW_LOAD_MATCH"::equals).count();
    }

    @Test
    void elAdminEscribeTodosLeenYElOperadorConsulta() {
        Session admin = register("CARRIER", "Pref Org Carrier");
        Session ops = member(admin, Profile.Role.OPS);
        Session viewer = member(admin, Profile.Role.VIEWER);
        as(viewer).get(PREFS).then().statusCode(200).body("data.routes", hasSize(0), "data.routeAlerts", is(true),
                "data.updatedAt", nullValue());

        as(admin).body(carrierPrefs(List.of(route("LIMA", "JULIACA"), route("AREQUIPA", "CUSCO")), 2.5, true,
                List.of("GENERAL"))).put(PREFS).then().statusCode(200)
                .body("data.routes", hasSize(2), "data.routes[0].origin", is("LIMA"), "data.minPricePerKm", is(2.5f),
                        "data.cargoTypes", contains("GENERAL"), "data.paymentTermsDays", nullValue());
        as(viewer).get(PREFS).then().statusCode(200).body("data.routes[1].destination", is("CUSCO"),
                "data.updatedAt", notNullValue());
        as(ops).body(carrierPrefs(List.of(), null, false, List.of())).put(PREFS).then().statusCode(403)
                .body("error.code", is("LOGYX-AUTH-008"));
        as(viewer).body(carrierPrefs(List.of(), null, false, List.of())).put(PREFS).then().statusCode(403)
                .body("error.code", is("LOGYX-AUTH-007"));

        var op = auth.provisionOperator("Pref Operador " + System.nanoTime() % 1000, Api.newRuc(), "Op Pref",
                "oppref" + System.nanoTime() + "@logyx.pe", "Operador123");
        Session operator = new Session(op.accessToken(), op.user().organization().id().toString(), null);
        as(operator).get("/api/v1/organizations/" + admin.orgId() + "/preferences").then().statusCode(200)
                .body("data.routes", hasSize(2), "data.organizationId", is(admin.orgId()));
        as(operator).get(PREFS).then().statusCode(403);
        as(admin).get("/api/v1/organizations/" + admin.orgId() + "/preferences").then().statusCode(403);
        // Otra organización no ve las mías: las suyas están vacías
        as(register("CARRIER", "Pref Org Otra")).get(PREFS).then().body("data.routes", hasSize(0));
    }

    @Test
    void laPymeGuardaRecojoYPlazoYSeValida() {
        Session pyme = register("PYME", "Pref Org Pyme");
        Map<String, Object> body = new HashMap<>(Map.of("cities", List.of("LIMA"), "routes", List.of(route("LIMA", "PUNO")),
                "defaultPickupFrom", "08:00", "defaultPickupTo", "12:30", "paymentTermsDays", 30, "minPricePerKm", 9));
        as(pyme).body(body).put(PREFS).then().statusCode(200).body("data.defaultPickupFrom", startsWith("08:00"),
                "data.defaultPickupTo", startsWith("12:30"), "data.paymentTermsDays", is(30),
                "data.minPricePerKm", nullValue());

        body.put("routes", List.of(route("LIMA", "LIMA")));
        as(pyme).body(body).put(PREFS).then().statusCode(400).body("error.code", is("LOGYX-ORG-010"), "error.field", is("routes"));
        body.put("routes", List.of());
        body.put("defaultPickupTo", "07:00");
        as(pyme).body(body).put(PREFS).then().statusCode(400).body("error.code", is("LOGYX-ORG-012"));
        body.put("defaultPickupTo", "12:30");
        body.put("paymentTermsDays", 121);
        as(pyme).body(body).put(PREFS).then().statusCode(400).body("error.field", is("paymentTermsDays"));
        body.put("paymentTermsDays", 15);
        body.put("cities", List.of("MIAMI"));
        as(pyme).body(body).put(PREFS).then().statusCode(400);                         // fuera del catálogo
        List<Map<String, String>> many = new ArrayList<>();
        String[] cities = {"LIMA", "ICA", "NAZCA", "AREQUIPA", "MOQUEGUA", "TACNA"};
        for (String a : cities) for (String b : cities) if (!a.equals(b)) many.add(route(a, b));
        body.put("cities", List.of());
        body.put("routes", many.subList(0, 21));
        as(pyme).body(body).put(PREFS).then().statusCode(400).body("error.code", is("LOGYX-ORG-011"));
        as(pyme).get(PREFS).then().body("data.paymentTermsDays", is(30), "data.routes[0].destination", is("PUNO"));
    }

    @Test
    void laAlertaImplicitaLlegaATodosSinDuplicarConLaBusquedaGuardada() {
        Session admin = register("CARRIER", "Pref Alerta Carrier");
        Session ops = member(admin, Profile.Role.OPS);
        vehicle(admin, plate(), 8000);
        as(admin).body(carrierPrefs(List.of(route("LIMA", "JULIACA")), null, true, List.of())).put(PREFS).then().statusCode(200);
        as(admin).body(Map.of("name", "Lima Juliaca mía", "originCity", "LIMA", "destinationCity", "JULIACA"))
                .post("/api/v1/saved-searches").then().statusCode(201);
        Session pyme = register("PYME", "Pref Alerta Pyme");

        String id = publish(pyme, "LIMA", "JULIACA", 1500);
        assertEquals(1, matches(admin), "una sola vez: ya le llegó por su búsqueda guardada");
        assertEquals(1, matches(ops), "el resto del equipo, por la ruta de la empresa");
        as(ops).get("/api/v1/notifications").then().body("data[0].relatedId", is(id),
                "data[0].title", startsWith("Carga nueva en tu ruta"), "data[0].mine", is(false));
        as(admin).get("/api/v1/notifications").then().body("data[0].body", containsString("Lima Juliaca mía"));

        publish(pyme, "LIMA", "PUNO", 1500);                                          // otra ruta
        publish(pyme, "JULIACA", "LIMA", 1500);                                       // sentido contrario
        publish(pyme, "LIMA", "JULIACA", 15000);                                      // no cabe en la flota
        assertEquals(1, matches(ops));

        // Respeta la preferencia de la persona: OPS apaga «Alertas de carga» en la app
        as(ops).body(Map.of("preferences", List.of(Map.of("category", "ALERTS", "inApp", false, "email", false))))
                .put("/api/v1/notifications/preferences").then().statusCode(200);
        publish(pyme, "LIMA", "JULIACA", 1200);
        assertEquals(1, matches(ops));
        assertEquals(2, matches(admin));
    }

    @Test
    void tiposPrecioPorKmApagadoYBloqueoFiltran() {
        Session carrier = register("CARRIER", "Pref Filtro Carrier");
        vehicle(carrier, plate(), 8000);
        Session pyme = register("PYME", "Pref Filtro Pyme");
        as(carrier).body(carrierPrefs(List.of(route("LIMA", "TACNA")), null, true, List.of("PERISHABLE")))
                .put(PREFS).then().statusCode(200);
        publish(pyme, "LIMA", "TACNA", 1000);                                         // GENERAL: no es su tipo
        assertEquals(0, matches(carrier));
        as(carrier).body(carrierPrefs(List.of(route("LIMA", "TACNA")), 99, true, List.of())).put(PREFS).then().statusCode(200);
        publish(pyme, "LIMA", "TACNA", 1000);                                         // S/ 99 por km: nadie paga eso
        assertEquals(0, matches(carrier));
        as(carrier).body(carrierPrefs(List.of(route("LIMA", "TACNA")), 0.01, false, List.of())).put(PREFS).then().statusCode(200);
        publish(pyme, "LIMA", "TACNA", 1000);                                         // alertas apagadas
        assertEquals(0, matches(carrier));
        as(carrier).body(carrierPrefs(List.of(route("LIMA", "TACNA")), 0.01, true, List.of())).put(PREFS).then().statusCode(200);
        publish(pyme, "LIMA", "TACNA", 1000);
        assertEquals(1, matches(carrier));
        // La PYME lo bloquea: no se entera de sus cargas
        as(pyme).body(Map.of("kind", "BLOCKED")).put("/api/v1/carrier-preferences/" + carrier.orgId()).then().statusCode(200);
        publish(pyme, "LIMA", "TACNA", 1000);
        assertEquals(1, matches(carrier));
        // Sin vehículo que pueda ofertar tampoco: otra organización sin flota
        Session noFleet = register("CARRIER", "Pref Sin Flota");
        as(noFleet).body(carrierPrefs(List.of(route("LIMA", "TACNA")), null, true, List.of())).put(PREFS).then().statusCode(200);
        publish(register("PYME", "Pref Filtro Pyme 2"), "LIMA", "TACNA", 1000);
        assertEquals(0, matches(noFleet));
    }

    @Test
    void elTopePorOrganizacionYHoraCorta() {
        Session carrier = register("CARRIER", "Pref Tope Carrier");
        vehicle(carrier, plate(), 8000);
        as(carrier).body(carrierPrefs(List.of(route("ICA", "MOQUEGUA")), null, true, List.of())).put(PREFS).then().statusCode(200);
        Session pyme = register("PYME", "Pref Tope Pyme");
        for (int i = 0; i < 21; i++) publish(pyme, "ICA", "MOQUEGUA", 500);
        assertEquals(20, matches(carrier), "máximo 20 alertas implícitas por organización y hora");
    }
}
