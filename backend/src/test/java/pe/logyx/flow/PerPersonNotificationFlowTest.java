package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.support.Api;
import pe.logyx.support.Flows;
import pe.logyx.support.Mailbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Zona D (roadmap v2 1.7, 1.8): avisos por persona (V16), preferencias de aviso, «Pedir acceso» y autoría.
 */
@QuarkusTest
class PerPersonNotificationFlowTest {

    @Inject AuthService auth;

    private Session member(Session admin, Profile.Role role) {
        String email = role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Persona " + role, role);
        return login(email, "Miembro1234");
    }

    private static int unread(Session s) {
        return as(s).get("/api/v1/notifications/unread-count").then().statusCode(200).extract().path("data.unread");
    }

    private static String placeBid(Session carrier, String requestId, float floor) {
        String v = vehicle(carrier, plate(), 8000);
        return as(carrier).body(Map.of("vehicleId", v, "amount", (float) Math.ceil(floor) + 200))
                .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201).extract().path("data.id");
    }

    @Test
    void unAvisoLeidoPorUnaPersonaSigueSinLeerParaSuCompanero() {
        Session admin = register("PYME", "Avisos Persona");
        Session ops = member(admin, Profile.Role.OPS);
        Session viewer = member(admin, Profile.Role.VIEWER);
        var req = publish(admin, 1500).statusCode(201).extract();
        String requestId = req.path("data.id");
        float floor = req.path("data.priceFloor");
        placeBid(register("CARRIER", "Avisos Persona C1"), requestId, floor);

        // Cada persona recibe su propia copia del NEW_BID
        for (Session s : List.of(admin, ops, viewer)) assertEquals(1, unread(s));
        String viewerCopy = as(viewer).get("/api/v1/notifications").then().statusCode(200)
                .body("data[0].type", is("NEW_BID")).extract().path("data[0].id");
        String adminCopy = as(admin).get("/api/v1/notifications").then().extract().path("data[0].id");
        assertNotEquals(viewerCopy, adminCopy, "una fila por persona");

        // El Lector lo marca como leído: para él sí, para sus compañeros no (antes desaparecía para todos, X-01/P-39)
        as(viewer).post("/api/v1/notifications/" + viewerCopy + "/read").then().statusCode(200).body("data.read", is(true));
        assertEquals(0, unread(viewer));
        assertEquals(1, unread(admin));
        assertEquals(1, unread(ops));
        as(admin).get("/api/v1/notifications?unread=true").then().body("data.id", contains(adminCopy));
        // Nadie marca la copia de otro
        as(ops).post("/api/v1/notifications/" + adminCopy + "/read").then().statusCode(404);
        assertEquals(1, unread(admin));
        // Marcar todo es solo lo propio
        as(ops).post("/api/v1/notifications/read-all").then().statusCode(200).body("data.updated", is(1));
        assertEquals(1, unread(admin));

        // «Tuyo»: la solicitud la publicó el ADMIN → solo su copia lleva mine = true
        as(admin).get("/api/v1/notifications").then().body("data[0].mine", is(true));
        as(ops).get("/api/v1/notifications").then().body("data[0].mine", is(false));
        as(admin).get("/api/v1/notifications?mine=true").then().body("meta.total", is(1));
        as(ops).get("/api/v1/notifications?mine=true").then().body("meta.total", is(0));
    }

    @Test
    void lasPreferenciasDecidenQueLlegaALaAppYQueSaleporCorreo() {
        Session admin = register("PYME", "Avisos Prefs");
        Session ops = member(admin, Profile.Role.OPS);
        Session viewer = member(admin, Profile.Role.VIEWER);

        // Valores por defecto, solo las categorías de una PYME (sin «Adjudicaciones» ni «Cargas combinadas»)
        as(ops).get("/api/v1/notifications/preferences").then().statusCode(200)
                .body("data.category", hasItems("BIDS", "SHIPMENTS", "ACCOUNT"))
                .body("data.category", not(hasItems("AWARDS", "SMART_LOAD")))
                .body("data.find { it.category == 'BIDS' }.inApp", is(true))
                .body("data.find { it.category == 'BIDS' }.email", is(false))
                .body("data.find { it.category == 'ACCOUNT' }.inAppLocked", is(true));

        // OPS apaga las ofertas en la app; el ADMIN las pide por correo. El Lector también puede cambiar las suyas.
        as(ops).body(Map.of("preferences", List.of(Map.of("category", "BIDS", "inApp", false, "email", false))))
                .put("/api/v1/notifications/preferences").then().statusCode(200)
                .body("data.find { it.category == 'BIDS' }.inApp", is(false));
        as(admin).body(Map.of("preferences", List.of(Map.of("category", "BIDS", "inApp", true, "email", true))))
                .put("/api/v1/notifications/preferences").then().statusCode(200);
        as(viewer).body(Map.of("preferences", List.of(Map.of("category", "NEGOTIATION", "inApp", true, "email", false))))
                .put("/api/v1/notifications/preferences").then().statusCode(200);
        // Errores: «Cuenta y accesos» siempre en la app; una categoría ajena a la PYME
        as(admin).body(Map.of("preferences", List.of(Map.of("category", "ACCOUNT", "inApp", false, "email", true))))
                .put("/api/v1/notifications/preferences").then().statusCode(400).body("error.code", is("LOGYX-NTF-001"));
        as(admin).body(Map.of("preferences", List.of(Map.of("category", "AWARDS", "inApp", true, "email", true))))
                .put("/api/v1/notifications/preferences").then().statusCode(400).body("error.code", is("LOGYX-NTF-002"));
        // Las preferencias son de cada persona
        as(viewer).get("/api/v1/notifications/preferences").then().body("data.find { it.category == 'BIDS' }.inApp", is(true));

        var req = publish(admin, 1500).statusCode(201).extract();
        placeBid(register("CARRIER", "Avisos Prefs C"), req.path("data.id"), req.path("data.priceFloor"));
        assertEquals(1, unread(admin));
        assertEquals(0, unread(ops), "OPS apagó las ofertas en la app");
        assertEquals(1, unread(viewer));
        var mail = Mailbox.await(admin.email(), 1);
        assertTrue(mail.getSubject().contains("Nueva oferta recibida"), mail.getSubject());
        assertTrue(mail.getText().contains("/cuenta/avisos"));
        Mailbox.settle();
        assertEquals(0, Mailbox.to(ops.email()).size(), "sin correo para quien no lo pidió");
    }

    @Test
    void elLectorPideAccesoYCadaAdminRecibeUnAvisoPersonal() {
        Session admin = register("PYME", "Pedir Acceso");
        Session admin2 = member(admin, Profile.Role.ADMIN);
        Session ops = member(admin, Profile.Role.OPS);
        Session viewer = member(admin, Profile.Role.VIEWER);

        as(viewer).body(Map.of("permission", "OPERATE", "reason", "Publico las cargas de Arequipa"))
                .post("/api/v1/organizations/me/access-requests").then().statusCode(202)
                .body("data.permission", is("OPERATE")).body("data.notifiedAdmins", is(2));
        for (Session a : List.of(admin, admin2)) {
            as(a).get("/api/v1/notifications").then().body("data[0].type", is("ACCESS_REQUESTED"))
                    .body("data[0].relatedType", is("USER")).body("data[0].mine", is(true))
                    .body("data[0].body", containsString("Publico las cargas de Arequipa"));
        }
        assertEquals(0, unread(ops), "el aviso es solo para los ADMIN");
        assertEquals(0, unread(viewer));

        // Otra vez enseguida: 429; el ADMIN ya tiene el permiso: 409; validación del permiso
        as(viewer).body(Map.of("permission", "OPERATE")).post("/api/v1/organizations/me/access-requests")
                .then().statusCode(429).body("error.code", is("LOGYX-ACC-002"));
        as(admin).body(Map.of("permission", "OPERATE")).post("/api/v1/organizations/me/access-requests")
                .then().statusCode(409).body("error.code", is("LOGYX-ACC-001"));
        as(ops).body(Map.of("permission", "NADA")).post("/api/v1/organizations/me/access-requests").then().statusCode(400);
        // OPS pide gestionar usuarios (no lo tiene): sí puede
        as(ops).body(Map.of("permission", "MANAGE_USERS")).post("/api/v1/organizations/me/access-requests")
                .then().statusCode(202).body("data.notifiedAdmins", is(2));
        // Los dos pedidos son avisos personales de los ADMIN
        as(admin).get("/api/v1/notifications?size=5").then().body("meta.total", is(2))
                .body("data.type", everyItem(is("ACCESS_REQUESTED")));
    }

    @Test
    void autoriaQuienPublicoYQuienHizoCadaAccionBajoRls() {
        Session admin = register("PYME", "Autoria");
        Session ops = member(admin, Profile.Role.OPS);
        Session viewer = member(admin, Profile.Role.VIEWER);
        Session carrier = register("CARRIER", "Autoria C");
        Session carrierOps = member(carrier, Profile.Role.OPS);
        Session stranger = register("PYME", "Autoria Ajena");

        var req = publish(ops, 1500).statusCode(201).extract();
        String requestId = req.path("data.id");
        String bidId = placeBid(carrierOps, requestId, req.path("data.priceFloor"));
        as(admin).post("/api/v1/bids/" + bidId + "/accept").then().statusCode(200);

        // La PYME (incluido su Lector) ve quién publicó y quién aceptó; no ve a las personas del transportista
        as(viewer).get("/api/v1/activity?subject=REQUEST&id=" + requestId).then().statusCode(200)
                .body("data.createdBy.name", is("Persona OPS")).body("data.createdBy.role", is("OPS"))
                .body("data.entries.action", hasItems("REQUEST_PUBLISHED", "BID_ACCEPTED"))
                .body("data.entries.action", not(hasItem("BID_PLACED")))
                .body("data.entries.find { it.action == 'BID_ACCEPTED' }.actor.name", startsWith("Admin Autoria"))
                .body("data.entries.find { it.action == 'REQUEST_PUBLISHED' }.own", is(true));
        // El transportista ve su propia oferta con su autor y nada de la PYME (ni el creador de la solicitud)
        as(carrier).get("/api/v1/activity?subject=BID&id=" + bidId).then().statusCode(200)
                .body("data.createdBy.name", is("Persona OPS"))
                .body("data.entries.action", hasItem("BID_PLACED"))
                .body("data.entries.find { it.action == 'BID_PLACED' }.actor.name", is("Persona OPS"));
        as(carrier).get("/api/v1/activity?subject=REQUEST&id=" + requestId).then().statusCode(200)
                .body("data.createdBy", nullValue())
                .body("data.entries.action", not(hasItems("REQUEST_PUBLISHED", "BID_ACCEPTED")));
        // Otra organización no ve el recurso
        as(stranger).get("/api/v1/activity?subject=REQUEST&id=" + requestId).then().statusCode(404);
        Api.raw(admin).get("/api/v1/activity?subject=REQUEST").then().statusCode(400);
    }
}
