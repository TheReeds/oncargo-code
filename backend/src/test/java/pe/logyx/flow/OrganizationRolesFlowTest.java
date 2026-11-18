package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Flows;
import pe.logyx.support.Mailbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** F03/F20/F31: roles de organización, usuarios, perfil de la organización y cuentas de conductores (V14). */
@QuarkusTest
class OrganizationRolesFlowTest {

    @Inject AuthService auth;
    @Inject SystemTx system;
    @Inject EntityManager em;

    private static final String USERS = "/api/v1/organizations/me/users";

    /** Usuario extra de la organización de {@code admin} con el rol dado (alta interna, como el seed). */
    private Session member(Session admin, Profile.Role role) {
        String email = role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Usuario " + role, role);
        return login(email, "Miembro1234");
    }

    private static String any() { return UUID.randomUUID().toString(); }

    @Test
    void viewerSoloLee() {
        Flows.Awarded a = Flows.awarded("Roles Viewer");
        Session pymeViewer = member(a.pyme(), Profile.Role.VIEWER);
        Session carrierViewer = member(a.carrier(), Profile.Role.VIEWER);

        // Escrituras: 403 LOGYX-AUTH-007 (denegación por defecto, antes del recurso)
        List.of(
                as(pymeViewer).body(Map.of()).post("/api/v1/shipment-requests"),                                // publicar
                as(pymeViewer).post("/api/v1/bids/" + a.bidId() + "/accept"),                                    // aceptar
                as(pymeViewer).body(Map.of("amount", 1000)).post("/api/v1/bids/" + a.bidId() + "/counter-offers"), // contraoferta
                as(pymeViewer).body(Map.of("reason", "x")).post("/api/v1/shipment-requests/" + a.requestId() + "/cancel"),
                as(pymeViewer).body(Map.of("reason", "x")).post("/api/v1/shipments/" + a.shipmentId() + "/cancel"),
                as(pymeViewer).body(Map.of("body", "hola")).post("/api/v1/shipment-requests/" + a.requestId()
                        + "/threads/" + a.carrier().orgId() + "/messages"),                                     // chatear
                as(carrierViewer).body(Map.of("vehicleId", any(), "amount", 1000))
                        .post("/api/v1/shipment-requests/" + any() + "/bids"),                                  // ofertar
                as(carrierViewer).body(Map.of("driverId", any())).put("/api/v1/shipments/" + a.shipmentId() + "/driver"),
                as(carrierViewer).body(Map.of("plate", plate(), "type", "TRUCK_MEDIUM", "maxWeightKg", 5000))
                        .post("/api/v1/vehicles"),
                as(carrierViewer).body(Map.of("status", "MAINTENANCE")).patch("/api/v1/vehicles/" + any() + "/status"),
                as(carrierViewer).post("/api/v1/smart-loads/generate")
        ).forEach(r -> r.then().statusCode(403).body("error.code", is("LOGYX-AUTH-007"),
                "error.message", containsString("Lector")));

        // Lecturas y autoservicio: 200
        as(pymeViewer).get("/api/v1/shipment-requests/" + a.requestId()).then().statusCode(200);
        as(pymeViewer).get("/api/v1/shipments/" + a.shipmentId()).then().statusCode(200);
        as(pymeViewer).get("/api/v1/organizations/me").then().statusCode(200);
        as(pymeViewer).get("/api/v1/auth/me").then().statusCode(200).body("data.role", is("VIEWER"),
                "data.permissions", contains("READ"));
        as(carrierViewer).get("/api/v1/vehicles").then().statusCode(200);
        as(carrierViewer).get("/api/v1/drivers").then().statusCode(200);
        as(pymeViewer).post("/api/v1/notifications/read-all").then().statusCode(200);
        as(pymeViewer).body(Map.of("originCity", "LIMA", "destinationCity", "JULIACA", "cargoType", "GENERAL",
                "weightKg", 1000)).post("/api/v1/pricing/calculate").then().statusCode(not(403));
        as(pymeViewer).body(Map.of("currentPassword", "Miembro1234", "newPassword", "Lector2026!"))
                .post("/api/v1/auth/change-password").then().statusCode(200);
    }

    @Test
    void opsOperaPeroNoGestionaUsuariosPerfilNiConductores() {
        Session pyme = register("PYME", "Roles Ops Pyme");
        Session carrier = register("CARRIER", "Roles Ops Carrier");
        Session pymeOps = member(pyme, Profile.Role.OPS);
        Session carrierOps = member(carrier, Profile.Role.OPS);

        String requestId = publish(pymeOps, 1500).statusCode(201).extract().path("data.id");
        String vehicleId = vehicle(carrierOps, plate(), 6000);                                 // flota de vehículos: sí
        String bidId = bid(carrierOps, requestId, vehicleId, 100);
        as(pymeOps).post("/api/v1/bids/" + bidId + "/accept").then().statusCode(200);
        as(pymeOps).get("/api/v1/auth/me").then().body("data.permissions", containsInAnyOrder("READ", "OPERATE"));

        // Solo ADMIN: 403 LOGYX-AUTH-008
        as(pymeOps).get(USERS).then().statusCode(403).body("error.code", is("LOGYX-AUTH-008"));
        as(pymeOps).body(Map.of("email", "x" + System.nanoTime() + "@test.pe", "fullName", "X", "role", "OPS"))
                .post(USERS).then().statusCode(403).body("error.code", is("LOGYX-AUTH-008"));
        as(pymeOps).body(Map.of("phone", "999888777")).patch("/api/v1/organizations/me").then().statusCode(403)
                .body("error.code", is("LOGYX-AUTH-008"));
        as(carrierOps).body(Map.of("fullName", "Chofer", "email", "c" + System.nanoTime() + "@test.pe",
                "password", "Chofer1234", "licenseNumber", "Q12345678")).post("/api/v1/drivers").then()
                .statusCode(403).body("error.code", is("LOGYX-AUTH-008"));
        String[] d = driver(carrier, "Roles Ops Chofer");
        as(carrierOps).post("/api/v1/drivers/" + d[0] + "/deactivate").then().statusCode(403)
                .body("error.code", is("LOGYX-AUTH-008"));
        as(carrierOps).body(Map.of("newPassword", "Nueva12345")).post("/api/v1/drivers/" + d[0] + "/reset-password")
                .then().statusCode(403);
        // ADMIN sí
        as(pyme).get(USERS).then().statusCode(200).body("data.size()", is(2));
        as(pyme).body(Map.of("phone", "999888777")).patch("/api/v1/organizations/me").then().statusCode(200)
                .body("data.phone", is("999888777"));
    }

    @Test
    void invitarAceptarYCambiarRol() {
        Session admin = register("PYME", "Roles Invita");
        String email = "invitado" + System.nanoTime() + "@test.pe";
        String id = as(admin).body(Map.of("email", email, "fullName", "Ana Invitada", "role", "OPS")).post(USERS)
                .then().statusCode(201).body("data.invitationPending", is(true), "data.role", is("OPS"))
                .extract().path("data.id");
        as(admin).body(Map.of("email", email, "fullName", "Otra", "role", "OPS")).post(USERS).then().statusCode(409)
                .body("error.code", is("LOGYX-AUTH-002"));
        as(admin).body(Map.of("email", "d" + email, "fullName", "Chofer", "role", "DRIVER")).post(USERS).then()
                .statusCode(400).body("error.code", is("LOGYX-USR-004"));

        var mail = Mailbox.await(email, 1);
        assertTrue(mail.getSubject().contains("Roles Invita"));
        // No puede entrar sin aceptar
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", "x12345678"))
                .post("/api/v1/auth/login").then().statusCode(401);
        as(admin).post(USERS + "/" + id + "/resend-invitation").then().statusCode(200);
        String token = Mailbox.token(Mailbox.await(email, 2));
        assertNotEquals(Mailbox.token(mail), token);
        given().contentType(ContentType.JSON).body(Map.of("token", token)).post("/api/v1/auth/password-tokens/inspect")
                .then().statusCode(200).body("data.purpose", is("INVITE"), "data.organizationName", is("Roles Invita"));
        given().contentType(ContentType.JSON).body(Map.of("token", token, "newPassword", "Invitada2026"))
                .post("/api/v1/auth/reset-password").then().statusCode(400).body("error.code", is("LOGYX-AUTH-011"));
        given().contentType(ContentType.JSON).body(Map.of("token", token, "newPassword", "Invitada2026",
                "acceptPrivacyPolicy", true)).post("/api/v1/auth/reset-password").then().statusCode(200);
        Session ops = login(email, "Invitada2026");
        as(admin).post(USERS + "/" + id + "/resend-invitation").then().statusCode(409)
                .body("error.code", is("LOGYX-USR-003"));
        as(admin).get(USERS).then().body("data.find { it.id == '" + id + "' }.invitationPending", is(false),
                "data.find { it.id == '" + id + "' }.lastLoginAt", notNullValue());

        // Cambio de rol: el token anterior deja de valer
        as(admin).body(Map.of("role", "VIEWER")).patch(USERS + "/" + id).then().statusCode(200)
                .body("data.role", is("VIEWER"));
        as(ops).get("/api/v1/auth/me").then().statusCode(401);
        Session viewer = login(email, "Invitada2026");
        as(viewer).get("/api/v1/auth/me").then().body("data.role", is("VIEWER"));
        publish(viewer, 1000).statusCode(403).body("error.code", is("LOGYX-AUTH-007"));
    }

    @Test
    void siempreQuedaUnAdminActivo() {
        Session admin = register("CARRIER", "Roles Ultimo Admin");
        String adminId = as(admin).get("/api/v1/auth/me").then().extract().path("data.id");
        as(admin).body(Map.of("role", "OPS")).patch(USERS + "/" + adminId).then().statusCode(409)
                .body("error.code", is("LOGYX-USR-001"));
        as(admin).post(USERS + "/" + adminId + "/deactivate").then().statusCode(409)
                .body("error.code", is("LOGYX-USR-002"));

        Session second = member(admin, Profile.Role.OPS);
        String secondId = as(second).get("/api/v1/auth/me").then().extract().path("data.id");
        as(admin).body(Map.of("role", "ADMIN")).patch(USERS + "/" + secondId).then().statusCode(200);
        Session secondAdmin = login(second.email(), "Miembro1234");
        // Con dos ADMIN: el primero puede pasar a OPS (cierra su sesión) …
        as(admin).body(Map.of("role", "OPS")).patch(USERS + "/" + adminId).then().statusCode(200);
        as(admin).get("/api/v1/auth/me").then().statusCode(401);
        // … y el que queda no puede degradarse ni desactivarse
        as(secondAdmin).body(Map.of("role", "VIEWER")).patch(USERS + "/" + secondId).then().statusCode(409)
                .body("error.code", is("LOGYX-USR-001"));
        // Desactivar a un usuario corta su acceso de inmediato; reactivarlo no revive el token viejo
        Session old = login(admin.email(), "Secreta123");
        as(secondAdmin).post(USERS + "/" + adminId + "/deactivate").then().statusCode(200).body("data.active", is(false));
        as(old).get("/api/v1/vehicles").then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("email", admin.email(), "password", "Secreta123"))
                .post("/api/v1/auth/login").then().statusCode(401);
        as(secondAdmin).post(USERS + "/" + adminId + "/reactivate").then().statusCode(200).body("data.active", is(true));
        as(old).get("/api/v1/vehicles").then().statusCode(401);
        login(admin.email(), "Secreta123");
    }

    @Test
    void adminNoVeNiModificaUsuariosDeOtraOrganizacion() {
        Session a = register("PYME", "Roles Org A");
        Session b = register("PYME", "Roles Org B");
        Session bOps = member(b, Profile.Role.OPS);
        String bOpsId = as(bOps).get("/api/v1/auth/me").then().extract().path("data.id");
        as(a).get(USERS).then().statusCode(200).body("data.id", not(hasItem(bOpsId)));
        as(a).body(Map.of("role", "VIEWER")).patch(USERS + "/" + bOpsId).then().statusCode(404);
        as(a).post(USERS + "/" + bOpsId + "/deactivate").then().statusCode(404);
        as(a).post(USERS + "/" + bOpsId + "/resend-invitation").then().statusCode(404);
        as(bOps).get("/api/v1/auth/me").then().statusCode(200);
    }

    @Test
    void cuentasDeConductoresSoloElAdmin() {
        Flows.Awarded a = Flows.awarded("Roles Chofer");
        String driverId = as(a.carrier()).get("/api/v1/drivers").then().extract().path("data[0].id");
        as(a.carrier()).post("/api/v1/drivers/" + driverId + "/deactivate").then().statusCode(200)
                .body("data.active", is(false));
        as(a.driver()).get("/api/v1/driver/shipments/active").then().statusCode(401);
        // No se le asignan envíos
        as(a.carrier()).body(Map.of("driverId", driverId, "reason", "relevo"))
                .put("/api/v1/shipments/" + a.shipmentId() + "/driver").then().statusCode(409)
                .body("error.code", is("LOGYX-FLT-003"), "error.field", is("driverId"));
        as(a.carrier()).post("/api/v1/drivers/" + driverId + "/reactivate").then().statusCode(200);
        String email = as(a.carrier()).get("/api/v1/drivers").then().extract().path("data[0].email");
        as(a.carrier()).body(Map.of("newPassword", "ChoferNuevo1")).post("/api/v1/drivers/" + driverId + "/reset-password")
                .then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", "Chofer1234"))
                .post("/api/v1/auth/login").then().statusCode(401);
        login(email, "ChoferNuevo1");
        // Otro transportista no ve ni toca a este conductor
        Session other = register("CARRIER", "Roles Chofer Ajeno");
        as(other).post("/api/v1/drivers/" + driverId + "/deactivate").then().statusCode(404);
    }

    @Test
    void perfilDeLaOrganizacion() {
        Session verified = register("CARRIER", "Perfil Verificada");
        as(verified).body(Map.of("city", "Arequipa", "address", "Av. Ejército 101")).patch("/api/v1/organizations/me")
                .then().statusCode(200).body("data.city", is("Arequipa"), "data.address", is("Av. Ejército 101"),
                        "data.legalDataEditable", is(false));
        as(verified).body(Map.of("legalName", "Otro Nombre SAC")).patch("/api/v1/organizations/me").then()
                .statusCode(409).body("error.code", is("LOGYX-ORG-007"), "error.field", is("legalName"));

        Session pending = registerPending("PYME", "Perfil Pendiente");
        as(pending).body(Map.of("legalName", "Perfil Pendiente Corregida SAC")).patch("/api/v1/organizations/me")
                .then().statusCode(200).body("data.legalName", is("Perfil Pendiente Corregida SAC"),
                        "data.verificationStatus", is("PENDING"));
        as(pending).body(Map.of("ruc", "20000000000")).patch("/api/v1/organizations/me").then().statusCode(400)
                .body("error.code", anyOf(is("LOGYX-ORG-001"), is("LOGYX-VAL-001")));
        String takenRuc = as(verified).get("/api/v1/organizations/me").then().extract().path("data.ruc");
        as(pending).body(Map.of("ruc", takenRuc)).patch("/api/v1/organizations/me").then().statusCode(409)
                .body("error.code", is("LOGYX-ORG-002"));
        as(pending).body(Map.of("legalName", "Escríbeme al 987654321 SAC")).patch("/api/v1/organizations/me").then()
                .statusCode(400).body("error.code", is("LOGYX-NEG-001"));

        // Rechazada: corregir el RUC la devuelve a PENDING
        system.run(() -> em.createNativeQuery("UPDATE organizations SET verification_status = 'REJECTED', "
                + "verification_note = 'RUC no coincide' WHERE id = ?1").setParameter(1, UUID.fromString(pending.orgId()))
                .executeUpdate());
        String newRuc = newRuc();
        as(pending).body(Map.of("ruc", newRuc)).patch("/api/v1/organizations/me").then().statusCode(200)
                .body("data.ruc", is(newRuc), "data.verificationStatus", is("PENDING"), "data.verificationNote", nullValue());
        as(pending).get("/api/v1/auth/me").then().body("data.verificationStatus", is("PENDING"));
    }
}
