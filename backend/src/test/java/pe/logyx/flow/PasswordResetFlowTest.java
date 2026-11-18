package pe.logyx.flow;

import io.quarkus.mailer.Mail;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Mailbox;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** F02: olvidé mi contraseña con enlace de un solo uso (V14, ADR-0009). */
@QuarkusTest
class PasswordResetFlowTest {

    @Inject SystemTx system;
    @Inject EntityManager em;

    private static io.restassured.response.ValidatableResponse forgot(String email) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email))
                .post("/api/v1/auth/forgot-password").then();
    }

    private static io.restassured.response.ValidatableResponse reset(String token, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("token", token, "newPassword", password))
                .post("/api/v1/auth/reset-password").then();
    }

    @Test
    void restablecerConEnlaceDeUnSoloUso() {
        Session s = register("PYME", "Reset Pyme");
        forgot(s.email()).statusCode(202).body("data.message", startsWith("Si el correo está registrado"));
        Mail mail = Mailbox.await(s.email(), 1);
        assertEquals("Restablece tu contraseña de OnCargo", mail.getSubject());
        assertTrue(mail.getHtml().contains("OnCargo"));
        String token = Mailbox.token(mail);
        assertTrue(mail.getText().contains("http://localhost:4321/restablecer?token=" + token));

        given().contentType(ContentType.JSON).body(Map.of("token", token))
                .post("/api/v1/auth/password-tokens/inspect").then().statusCode(200)
                .body("data.purpose", is("RESET"), "data.email", endsWith("@test.pe"),
                        "data.email", not(is(s.email())), "data.expiresAt", notNullValue());

        reset(token, "corta").statusCode(400).body("error.code", is("LOGYX-VAL-001"));
        reset(token, "NuevaClave2026").statusCode(200);
        // Un solo uso
        reset(token, "OtraClave2026").statusCode(400).body("error.code", is("LOGYX-AUTH-005"), "error.field", is("token"));
        // La sesión anterior queda cerrada; la contraseña vieja ya no sirve, la nueva sí
        as(s).get("/api/v1/auth/me").then().statusCode(401).body("error.code", is("LOGYX-AUTH-401"));
        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "password", "Secreta123"))
                .post("/api/v1/auth/login").then().statusCode(401);
        login(s.email(), "NuevaClave2026");
        // Basura o tokens inexistentes: mismo error genérico
        reset("x".repeat(43), "NuevaClave2026").statusCode(400).body("error.code", is("LOGYX-AUTH-005"));
        reset("no-es-un-token", "NuevaClave2026").statusCode(400).body("error.code", is("LOGYX-AUTH-005"));
    }

    @Test
    void enlaceVencidoOReemplazadoNoSirve() {
        Session s = register("CARRIER", "Reset Vencido");
        forgot(s.email()).statusCode(202);
        String first = Mailbox.token(Mailbox.await(s.email(), 1));
        forgot(s.email()).statusCode(202);
        String second = Mailbox.token(Mailbox.await(s.email(), 2));
        assertNotEquals(first, second);
        reset(first, "NuevaClave2026").statusCode(400).body("error.code", is("LOGYX-AUTH-005"));  // reemplazado
        // Vence: se retrocede su vencimiento como dueño del esquema
        system.run(() -> em.createNativeQuery("UPDATE password_tokens SET expires_at = now() - interval '1 minute' "
                + "WHERE profile_id = (SELECT id FROM profiles WHERE email = ?1) AND used_at IS NULL")
                .setParameter(1, s.email()).executeUpdate());
        reset(second, "NuevaClave2026").statusCode(400).body("error.code", is("LOGYX-AUTH-005"));
        given().contentType(ContentType.JSON).body(Map.of("token", second))
                .post("/api/v1/auth/password-tokens/inspect").then().statusCode(400);
    }

    @Test
    void mismaRespuestaSiElCorreoNoExisteYLimitePorEmailSinCambiarLaRespuesta() {
        String unknown = "nadie" + System.nanoTime() + "@test.pe";
        String a = forgot(unknown).statusCode(202).extract().path("data.message");
        Session s = register("PYME", "Reset Limite");
        String b = forgot(s.email()).statusCode(202).extract().path("data.message");
        assertEquals(a, b);
        Mailbox.settle();
        assertTrue(Mailbox.to(unknown).isEmpty());

        // 3 por email cada 15 min: el 4.º responde igual pero no envía
        forgot(s.email()).statusCode(202);
        forgot(s.email()).statusCode(202);
        Mailbox.await(s.email(), 3);
        String d = forgot(s.email()).statusCode(202).extract().path("data.message");
        assertEquals(a, d);
        Mailbox.settle();
        assertEquals(3, Mailbox.to(s.email()).size());
    }

    @Test
    void cuentaDesactivadaNoRecibeEnlace() {
        Session s = register("PYME", "Reset Inactiva");
        system.run(() -> em.createNativeQuery("UPDATE profiles SET active = false WHERE email = ?1")
                .setParameter(1, s.email()).executeUpdate());
        forgot(s.email()).statusCode(202);
        Mailbox.settle();
        assertTrue(Mailbox.to(s.email()).isEmpty());
    }
}
