package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Mailbox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Roadmap 2.17 (V24): los Términos se aceptan también al aceptar una invitación ({@code source = INVITATION}) y al
 * cambiar la versión vigente ({@code GET /auth/me → termsAccepted}, {@code POST /auth/terms/accept} → {@code UPDATE}
 * bajo RLS). No bloquea la API: la web pide la aceptación.
 */
@QuarkusTest
class TermsUpdateFlowTest {

    @Inject AuthService auth;
    @Inject SystemTx systemTx;
    @Inject EntityManager em;

    private static String current() {
        return given().get("/api/v1/public/legal").then().statusCode(200).extract().path("data.termsVersion");
    }

    @SuppressWarnings("unchecked")
    private List<String> sources(String email) {
        return systemTx.call(() -> em.createNativeQuery("SELECT t.source FROM terms_acceptances t JOIN profiles p "
                + "ON p.id = t.profile_id WHERE p.email = ?1 ORDER BY t.accepted_at").setParameter(1, email).getResultList());
    }

    private static io.restassured.response.ValidatableResponse redeem(Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/v1/auth/reset-password").then();
    }

    @Test
    void alAceptarUnaInvitacionTambienSeAceptanLosTerminos() {
        Session admin = register("PYME", "Terminos Invita");
        String email = "invterm" + System.nanoTime() + "@test.pe";
        as(admin).body(Map.of("email", email, "fullName", "Invitada Términos", "role", "OPS"))
                .post("/api/v1/organizations/me/users").then().statusCode(201);
        String token = Mailbox.token(Mailbox.await(email, 1));
        Map<String, Object> body = new HashMap<>(Map.of("token", token, "newPassword", "Invitada2026",
                "acceptPrivacyPolicy", true));
        redeem(body).statusCode(400).body("error.code", is("LOGYX-AUTH-013"), "error.field", is("acceptTerms"));
        body.put("acceptTerms", true);
        body.put("termsVersion", "2020-01-01-vieja");
        redeem(body).statusCode(409).body("error.code", is("LOGYX-AUTH-012"), "error.field", is("termsVersion"));
        body.put("termsVersion", current());
        redeem(body).statusCode(200);
        assertEquals(List.of("INVITATION"), sources(email));
        as(login(email, "Invitada2026")).get("/api/v1/auth/me").then().statusCode(200)
                .body("data.termsAccepted", is(true), "data.termsVersion", is(current()));
        // El token ya se usó: no deja otra aceptación
        redeem(body).statusCode(400).body("error.code", is("LOGYX-AUTH-005"));
        assertEquals(1, sources(email).size());
    }

    @Test
    void conOtraVersionLaPersonaLaAceptaUnaVezBajoSuSesion() {
        Session registered = register("PYME", "Terminos Version");
        as(registered).get("/api/v1/auth/me").then().body("data.termsAccepted", is(true));
        // Persona sin aceptación de la versión vigente (aceptó una anterior): termsAccepted = false, la API no se bloquea
        String email = "termupd" + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(registered.orgId()), email, "Lectora1234", "Lectora Términos", Profile.Role.VIEWER);
        systemTx.run(() -> em.createNativeQuery("INSERT INTO terms_acceptances (id, profile_id, organization_id, terms_version, "
                + "source) SELECT gen_random_uuid(), id, organization_id, '2025-01-01-anterior', 'REGISTER' FROM profiles "
                + "WHERE email = ?1").setParameter(1, email).executeUpdate());
        Session viewer = login(email, "Lectora1234");
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", "Lectora1234"))
                .post("/api/v1/auth/login").then().statusCode(200).body("data.user.termsAccepted", is(false));
        as(viewer).get("/api/v1/auth/me").then().statusCode(200).body("data.termsAccepted", is(false),
                "data.termsVersion", is(current()));
        as(viewer).get("/api/v1/shipment-requests/mine").then().statusCode(200);

        as(viewer).body(Map.of("termsVersion", "2025-01-01-anterior")).post("/api/v1/auth/terms/accept").then()
                .statusCode(409).body("error.code", is("LOGYX-AUTH-012"));
        as(viewer).body(Map.of()).post("/api/v1/auth/terms/accept").then().statusCode(400);
        as(viewer).body(Map.of("termsVersion", current())).post("/api/v1/auth/terms/accept").then().statusCode(200)
                .body("data.termsAccepted", is(true), "data.termsVersion", is(current()));
        as(viewer).body(Map.of("termsVersion", current())).post("/api/v1/auth/terms/accept").then().statusCode(200);
        assertEquals(List.of("REGISTER", "UPDATE"), sources(email), "idempotente: una sola fila por versión");
        as(viewer).get("/api/v1/auth/me").then().body("data.termsAccepted", is(true));
    }
}
