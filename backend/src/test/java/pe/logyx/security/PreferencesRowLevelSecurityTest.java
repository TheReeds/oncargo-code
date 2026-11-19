package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.support.Api.Session;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * RLS de la Ola 2 zona F · 2.2 y 2.17 (V24), directamente en PostgreSQL y sin filtros por organización ni persona:
 * {@code user_preferences} es de cada persona, {@code organization_preferences} de cada organización (el operador lee),
 * {@code notification_email_queue} y {@code org_route_alerts} son solo del sistema y la persona solo inserta su propia
 * aceptación de los términos con {@code source = 'UPDATE'}.
 */
@QuarkusTest
class PreferencesRowLevelSecurityTest {

    @Inject EntityManager em;
    @Inject AuthService auth;

    private static String profileOf(Session s) {
        String payload = s.token().split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("token sin sub");
        return m.group(1);
    }

    private <T> T as(Session s, String orgType, java.util.function.Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                            + "set_config('app.profile_id', ?3, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return work.get();
        });
    }

    private long countAs(Session s, String orgType, String sql) {
        return as(s, orgType, () -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }

    private int executeAs(Session s, String orgType, String sql) {
        return as(s, orgType, () -> em.createNativeQuery(sql).executeUpdate());
    }

    private Session member(Session admin, Profile.Role role) {
        String email = "rlspref" + role.name().toLowerCase() + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Persona " + role, role);
        return login(email, "Miembro1234");
    }

    @Test
    void lasPreferenciasDeCorreoSonDeCadaPersona() {
        Session admin = register("PYME", "RLS Pref Persona");
        Session ops = member(admin, Profile.Role.OPS);
        Session other = register("PYME", "RLS Pref Otra");
        pe.logyx.support.Api.as(admin).body(Map.of("emailEnabled", false, "mode", "DAILY", "digestHour", 9))
                .put("/api/v1/notifications/email-settings").then().statusCode(200);
        String mine = "SELECT count(*) FROM user_preferences";
        assertEquals(1L, countAs(admin, "PYME", mine));
        assertEquals(0L, countAs(ops, "PYME", mine), "un compañero no ve las mías");
        assertEquals(0L, countAs(other, "PYME", mine));
        assertEquals(0, executeAs(ops, "PYME", "UPDATE user_preferences SET email_enabled = true"));
        // Nadie inserta por otra persona ni las borra
        assertThrows(Exception.class, () -> executeAs(ops, "PYME", "INSERT INTO user_preferences (profile_id, org_id) "
                + "VALUES ('" + profileOf(admin) + "', '" + admin.orgId() + "')"));
        assertThrows(Exception.class, () -> executeAs(admin, "PYME", "DELETE FROM user_preferences"));
        pe.logyx.support.Api.as(admin).get("/api/v1/notifications/email-settings").then().statusCode(200)
                .body("data.emailEnabled", org.hamcrest.Matchers.is(false));
    }

    @Test
    void lasPreferenciasDeLaOrganizacionSonSuyasYElOperadorLasLee() {
        Session carrier = register("CARRIER", "RLS Pref Org");
        Session other = register("CARRIER", "RLS Pref Org Otra");
        pe.logyx.support.Api.as(carrier).body(Map.of("routes", List.of(Map.of("origin", "LIMA", "destination", "PUNO"))))
                .put("/api/v1/organizations/me/preferences").then().statusCode(200);
        var op = auth.provisionOperator("RLS Pref Op " + System.nanoTime() % 1000, newRuc(), "Op Pref",
                "rlsprefop" + System.nanoTime() + "@logyx.pe", "Operador123");
        Session operator = new Session(op.accessToken(), op.user().organization().id().toString(), null);
        String one = "SELECT count(*) FROM organization_preferences WHERE organization_id = '" + carrier.orgId() + "'";
        assertEquals(1L, countAs(carrier, "CARRIER", one));
        assertEquals(0L, countAs(other, "CARRIER", one));
        assertEquals(1L, countAs(operator, "OPERATOR", one));
        assertEquals(0, executeAs(other, "CARRIER", "UPDATE organization_preferences SET route_alerts = false"));
        assertEquals(0, executeAs(operator, "OPERATOR", "UPDATE organization_preferences SET route_alerts = false"),
                "el operador solo lee");
        assertThrows(Exception.class, () -> executeAs(other, "CARRIER", "INSERT INTO organization_preferences "
                + "(organization_id) VALUES ('" + carrier.orgId() + "')"));
        assertThrows(Exception.class, () -> executeAs(carrier, "CARRIER", "DELETE FROM organization_preferences"));
    }

    @Test
    void laColaDeCorreoYLaBitacoraDeAlertasSonSoloDelSistema() {
        Session pyme = register("PYME", "RLS Pref Cola");
        assertThrows(Exception.class, () -> countAs(pyme, "PYME", "SELECT count(*) FROM notification_email_queue"));
        assertThrows(Exception.class, () -> countAs(pyme, "PYME", "SELECT count(*) FROM org_route_alerts"));
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME", "INSERT INTO notification_email_queue (id, profile_id, "
                + "org_id, category, title, body, reason, send_after) VALUES (gen_random_uuid(), '" + profileOf(pyme) + "', '"
                + pyme.orgId() + "', 'BIDS', 't', 'b', 'QUIET', now())"));
    }

    @Test
    void laPersonaSoloInsertaSuPropiaAceptacionDeTerminos() {
        Session admin = register("PYME", "RLS Pref Terminos");
        Session ops = member(admin, Profile.Role.OPS);
        String insert = "INSERT INTO terms_acceptances (id, profile_id, organization_id, terms_version, source) VALUES "
                + "(gen_random_uuid(), '%s', '" + admin.orgId() + "', 'v-test', '%s')";
        assertEquals(1, executeAs(ops, "PYME", insert.formatted(profileOf(ops), "UPDATE")));
        assertThrows(Exception.class, () -> executeAs(ops, "PYME", insert.formatted(profileOf(admin), "UPDATE")),
                "por otra persona no");
        assertThrows(Exception.class, () -> executeAs(ops, "PYME", insert.formatted(profileOf(ops), "INVITATION")),
                "la de invitación la escribe la plataforma");
        assertThrows(Exception.class, () -> executeAs(ops, "PYME", "UPDATE terms_acceptances SET terms_version = 'x'"));
        assertThrows(Exception.class, () -> executeAs(ops, "PYME", "DELETE FROM terms_acceptances"));
    }
}
