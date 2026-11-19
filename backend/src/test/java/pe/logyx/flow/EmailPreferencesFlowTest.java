package pe.logyx.flow;

import io.quarkus.mailer.Mail;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.notification.application.EmailQueueService;
import pe.logyx.settlement.application.event.PaymentDue;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Mailbox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Roadmap 2.2 (V24): correo de avisos según las preferencias de la persona (correo activo, inmediato o resumen diario,
 * horario de silencio), cola de correos diferidos con un correo por persona, y baja sin login con token HMAC
 * (List-Unsubscribe one-click, RFC 8058). Los avisos se disparan con {@link PaymentDue} (categoría «Cobros y pagos»,
 * correo por omisión) como lo haría el barrido de cobros.
 */
@QuarkusTest
class EmailPreferencesFlowTest {

    private static final String SETTINGS = "/api/v1/notifications/email-settings";
    private static final Pattern LEAVE = Pattern.compile("/baja\\?t=([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject Event<PaymentDue> paymentDue;
    @Inject SystemTx systemTx;
    @Inject EntityManager em;
    @Inject EmailQueueService queue;
    @Inject AuthService auth;

    private void remind(Session pyme, String code) {
        PaymentDue e = new PaymentDue(UUID.randomUUID(), UUID.fromString(pyme.orgId()), UUID.randomUUID(), code,
                "Lima → Juliaca", "DUE_SOON", LocalDate.now().plusDays(3), 0, new BigDecimal("1180.00"));
        systemTx.run(() -> paymentDue.fire(e));
    }

    private static int unread(Session s) {
        return as(s).get("/api/v1/notifications/unread-count").then().statusCode(200).extract().path("data.unread");
    }

    private static String leaveToken(Mail m) {
        Matcher x = LEAVE.matcher(m.getText());
        assertTrue(x.find(), "el correo trae el enlace de baja");
        return x.group(1);
    }

    private static io.restassured.response.ValidatableResponse publicPost(String path, Map<String, Object> body) {
        return given().contentType("application/json").body(body).post("/api/v1/public/unsubscribe" + path).then();
    }

    private long queued(Session s, String extra) {
        return systemTx.call(() -> ((Number) em.createNativeQuery("SELECT count(*) FROM notification_email_queue q "
                + "JOIN profiles p ON p.id = q.profile_id WHERE p.email = ?1 " + extra)
                .setParameter(1, s.email()).getSingleResult()).longValue());
    }

    private Session member(Session admin, Profile.Role role) {
        String email = "mail" + role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Persona " + role, role);
        return login(email, "Miembro1234");
    }

    private static Map<String, Object> settings(boolean enabled, String mode, Integer hour, String from, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("emailEnabled", enabled);
        m.put("mode", mode);
        if (hour != null) m.put("digestHour", hour);
        m.put("quietFrom", from);
        m.put("quietTo", to);
        return m;
    }

    @Test
    void elCorreoInmediatoTraeBajaYCabecerasOneClickYLaBajaApagaSoloEsaCategoria() {
        Session pyme = register("PYME", "Correo Baja");
        as(pyme).get(SETTINGS).then().statusCode(200).body("data.emailEnabled", is(true), "data.mode", is("IMMEDIATE"),
                "data.digestHour", is(7), "data.quietFrom", nullValue());
        remind(pyme, "OC-MAIL1");
        Mail m = Mailbox.await(pyme.email(), 1);
        assertTrue(m.getSubject().startsWith("Pago por vencer · OC-MAIL1"), m.getSubject());
        assertTrue(m.getText().contains("S/ 1,180.00"));
        String token = leaveToken(m);
        assertEquals(List.of("<http://localhost:4321/baja/one-click?t=" + token + ">"), m.getHeaders().get("List-Unsubscribe"));
        assertEquals(List.of("List-Unsubscribe=One-Click"), m.getHeaders().get("List-Unsubscribe-Post"));
        assertTrue(m.getHtml().contains("Darte de baja"));

        // Lo que ve la página de baja: lista blanca y email enmascarado
        Map<String, Object> info = publicPost("/inspect", Map.of("token", token)).statusCode(200)
                .header("X-Robots-Tag", containsString("noindex")).extract().path("data");
        assertEquals(Set.of("category", "categoryLabel", "email", "categoryEmail", "emailEnabled"), info.keySet());
        assertEquals("PAYMENTS", info.get("category"));
        assertEquals("Cobros y pagos", info.get("categoryLabel"));
        assertFalse(((String) info.get("email")).contains(pyme.email().substring(3, pyme.email().indexOf('@'))));
        assertTrue(((String) info.get("email")).endsWith("@test.pe"));

        // One-click (lo que reenvía la web): apaga el correo de la categoría, el aviso en la app sigue
        publicPost("", Map.of("token", token)).statusCode(200).body("data.categoryEmail", is(false),
                "data.emailEnabled", is(true));
        publicPost("", Map.of("token", token)).statusCode(200);                        // idempotente
        as(pyme).get("/api/v1/notifications/preferences").then().statusCode(200)
                .body("data.find { it.category == 'PAYMENTS' }.email", is(false))
                .body("data.find { it.category == 'PAYMENTS' }.inApp", is(true));
        remind(pyme, "OC-MAIL2");
        Mailbox.settle();
        assertEquals(1, Mailbox.to(pyme.email()).size(), "sin correo de esa categoría");
        assertEquals(2, unread(pyme), "en la app sí");
    }

    @Test
    void unTokenAlteradoOAjenoNoSirveYLaBajaDeTodoSeDeshaceDesdeLaCuenta() {
        Session a = register("PYME", "Correo Alterado A");
        Session b = register("PYME", "Correo Alterado B");
        remind(a, "OC-ALT1");
        remind(b, "OC-ALT2");
        String ta = leaveToken(Mailbox.await(a.email(), 1));
        String tb = leaveToken(Mailbox.await(b.email(), 1));
        // Firma de A con el perfil de B (o al revés), un carácter cambiado o basura: misma respuesta 404
        String forged = tb.substring(0, tb.indexOf('.')) + ta.substring(ta.indexOf('.'));
        for (String bad : List.of(forged, ta.substring(0, ta.length() - 2) + "xx", "nada", "a.b")) {
            publicPost("/inspect", Map.of("token", bad)).statusCode(404).body("error.code", is("LOGYX-NTF-003"));
            publicPost("", Map.of("token", bad, "scope", "ALL")).statusCode(404).body("error.code", is("LOGYX-NTF-003"));
        }
        publicPost("", Map.of()).statusCode(400);

        // B se da de baja de todo: A sigue recibiendo
        publicPost("", Map.of("token", tb, "scope", "ALL")).statusCode(200).body("data.emailEnabled", is(false));
        as(b).get(SETTINGS).then().body("data.emailEnabled", is(false), "data.unsubscribedAt", notNullValue());
        remind(a, "OC-ALT3");
        remind(b, "OC-ALT4");
        Mailbox.await(a.email(), 2);
        Mailbox.settle();
        assertEquals(1, Mailbox.to(b.email()).size());
        assertEquals(0, queued(b, ""), "con el correo apagado ni siquiera se encola");

        // B vuelve a activar el correo desde su cuenta
        as(b).body(settings(true, "IMMEDIATE", null, null, null)).put(SETTINGS).then().statusCode(200)
                .body("data.emailEnabled", is(true), "data.unsubscribedAt", nullValue());
        remind(b, "OC-ALT5");
        Mailbox.await(b.email(), 2);
    }

    @Test
    void elSilencioYElResumenEncolanYLaColaEnviaUnCorreoPorPersona() {
        Session quiet = register("PYME", "Correo Silencio");
        ZonedDateTime now = ZonedDateTime.now(LIMA).truncatedTo(ChronoUnit.MINUTES);
        String from = now.minusHours(1).toLocalTime().toString();
        String to = now.plusHours(1).toLocalTime().toString();
        as(quiet).body(settings(true, "IMMEDIATE", null, from, to)).put(SETTINGS).then().statusCode(200)
                .body("data.quietFrom", startsWith(from.substring(0, 5)));
        remind(quiet, "OC-Q1");
        remind(quiet, "OC-Q2");
        Mailbox.settle();
        assertEquals(0, Mailbox.to(quiet.email()).size(), "en silencio no sale nada");
        assertEquals(2, queued(quiet, "AND q.reason = 'QUIET' AND q.sent_at IS NULL"));
        assertEquals(2, unread(quiet), "el aviso en la app llega igual");

        Session digest = register("PYME", "Correo Resumen");
        int hour = now.plusHours(2).getHour();
        as(digest).body(settings(true, "DAILY", hour, null, null)).put(SETTINGS).then().statusCode(200)
                .body("data.mode", is("DAILY"), "data.digestHour", is(hour));
        remind(digest, "OC-D1");
        Mailbox.settle();
        assertEquals(0, Mailbox.to(digest.email()).size());
        assertEquals(1, queued(digest, "AND q.reason = 'DIGEST'"));

        // Otra persona encola y apaga el correo antes del envío: se marca y no recibe nada
        Session late = register("PYME", "Correo Tarde");
        as(late).body(settings(true, "IMMEDIATE", null, from, to)).put(SETTINGS).then().statusCode(200);
        remind(late, "OC-L1");
        as(late).body(settings(false, "IMMEDIATE", null, from, to)).put(SETTINGS).then().statusCode(200);

        queue.flushDue(OffsetDateTime.now());                              // aún no vence nada de esto
        assertEquals(2, queued(quiet, "AND q.sent_at IS NULL"));
        queue.flushDue(OffsetDateTime.now().plusHours(3));
        Mail summary = Mailbox.await(quiet.email(), 1);
        assertEquals("Tienes 2 avisos · OnCargo", summary.getSubject());
        assertTrue(summary.getText().contains("OC-Q1") && summary.getText().contains("OC-Q2"));
        assertNotNull(summary.getHeaders().get("List-Unsubscribe"));
        Mail single = Mailbox.await(digest.email(), 1);
        assertTrue(single.getSubject().startsWith("Pago por vencer · OC-D1"), single.getSubject());
        assertEquals(0, queued(quiet, "AND q.sent_at IS NULL"));
        assertEquals(0, queued(late, "AND q.sent_at IS NULL"), "marcado aunque no se envió");
        queue.flushDue(OffsetDateTime.now().plusHours(3));                 // ya enviados: no se repiten
        Mailbox.settle();
        assertEquals(1, Mailbox.to(quiet.email()).size());
        assertEquals(0, Mailbox.to(late.email()).size());
    }

    @Test
    void elLectorCambiaSusPreferenciasDeCorreoYSeValidaElSilencio() {
        Session admin = register("PYME", "Correo Lector");
        Session viewer = member(admin, Profile.Role.VIEWER);
        as(viewer).body(settings(true, "DAILY", 18, "22:00", "07:00")).put(SETTINGS).then().statusCode(200)
                .body("data.mode", is("DAILY"), "data.quietFrom", startsWith("22:00"), "data.quietTo", startsWith("07:00"));
        as(viewer).body(settings(true, "IMMEDIATE", null, "22:00", null)).put(SETTINGS).then().statusCode(400)
                .body("error.code", is("LOGYX-NTF-004"), "error.field", is("quietTo"));
        as(viewer).body(settings(true, "IMMEDIATE", null, "22:00", "22:00")).put(SETTINGS).then().statusCode(400)
                .body("error.code", is("LOGYX-NTF-004"));
        as(viewer).body(settings(true, "DAILY", 24, null, null)).put(SETTINGS).then().statusCode(400)
                .body("error.code", is("LOGYX-VAL-001"));
        // Son de cada persona: el ADMIN conserva los valores por omisión
        as(admin).get(SETTINGS).then().body("data.mode", is("IMMEDIATE"), "data.quietFrom", nullValue());
        as(viewer).get(SETTINGS).then().body("data.digestHour", is(18));
        // El conductor no tiene centro de avisos
        String[] driver = driver(register("CARRIER", "Correo Chofer"), "Chofer Correo");
        as(login(driver[1], "Chofer1234")).get(SETTINGS).then().statusCode(403);
    }
}
