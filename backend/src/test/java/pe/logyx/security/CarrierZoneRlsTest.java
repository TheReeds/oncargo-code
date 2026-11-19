package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Flows;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * RLS de la Ola 2, zona T (V22), con consultas que NO filtran por organización: búsquedas guardadas (por persona),
 * documentos de flota (privados del transportista), historial de mejoras de oferta (solo del dueño), bitácoras del
 * sistema (sin acceso para la app) y el historial de precios por ruta (solo agregados con k >= 5).
 */
@QuarkusTest
class CarrierZoneRlsTest {

    @Inject EntityManager em;
    @Inject AuthService auth;
    @Inject SystemTx systemTx;

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

    private int executeAs(Session s, String orgType, String sql) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                            + "set_config('app.profile_id', ?3, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return em.createNativeQuery(sql).executeUpdate();
        });
    }

    private long countAsOwner(String sql) {
        return systemTx.call(() -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }

    private Session operator() {
        var r = auth.provisionOperator("RLS Zona T " + System.nanoTime() % 1000, Api.newRuc(), "Op",
                "op" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    private Session member(Session admin) {
        String email = "ops" + System.nanoTime() + "@test.pe";
        auth.provisionUser(UUID.fromString(admin.orgId()), email, "Miembro1234", "Persona OPS", Profile.Role.OPS);
        return login(email, "Miembro1234");
    }

    @Test
    void lasBusquedasGuardadasSonDeCadaPersona() {
        Session admin = register("CARRIER", "RLS Alertas");
        Session ops = member(admin);
        as(admin).body(Map.of("name", "Mía", "originCity", "LIMA")).post("/api/v1/saved-searches").then().statusCode(201);
        assertEquals(1, countAs(admin, "CARRIER", "SELECT count(*) FROM saved_searches"));
        assertEquals(0, countAs(ops, "CARRIER", "SELECT count(*) FROM saved_searches"), "otra persona de la misma organización");
        assertEquals(0, countAs(register("CARRIER", "RLS Alertas Otra"), "CARRIER", "SELECT count(*) FROM saved_searches"));
        assertEquals(0, countAs(register("PYME", "RLS Alertas Pyme"), "PYME", "SELECT count(*) FROM saved_searches"));
        assertEquals(0, countAs(operator(), "OPERATOR", "SELECT count(*) FROM saved_searches"));
        // No puede crear una búsqueda a nombre de otra persona
        assertThrows(PersistenceException.class, () -> executeAs(ops, "CARRIER",
                "INSERT INTO saved_searches (organization_id, profile_id, name, origin_city) VALUES ('" + admin.orgId()
                        + "', '" + profileOf(admin) + "', 'Suplantada', 'LIMA')"));
    }

    @Test
    void losDocumentosDeFlotaSonPrivadosDelTransportista() {
        Session carrier = register("CARRIER", "RLS Docs");
        String v = vehicle(carrier, plate(), 8000);
        raw(carrier).multiPart("file", "soat.pdf", PDF, "application/pdf").multiPart("type", "SOAT")
                .multiPart("expiresAt", LocalDate.now().plusDays(100).toString())
                .post("/api/v1/vehicles/" + v + "/documents").then().statusCode(201);
        assertEquals(1, countAs(carrier, "CARRIER", "SELECT count(*) FROM fleet_documents"));
        assertEquals(0, countAs(register("CARRIER", "RLS Docs Otro"), "CARRIER", "SELECT count(*) FROM fleet_documents"));
        assertEquals(0, countAs(register("PYME", "RLS Docs Pyme"), "PYME", "SELECT count(*) FROM fleet_documents"));
        assertTrue(countAs(operator(), "OPERATOR", "SELECT count(*) FROM fleet_documents") >= 1, "el operador lee");
        // Inmutables: ni borrar ni cambiar el vencimiento (solo la marca de reemplazo)
        assertThrows(PersistenceException.class, () -> executeAs(carrier, "CARRIER", "DELETE FROM fleet_documents"));
        assertThrows(PersistenceException.class, () -> executeAs(carrier, "CARRIER",
                "UPDATE fleet_documents SET expires_at = now() + interval '5 years'"));
        assertEquals(1, executeAs(carrier, "CARRIER", "UPDATE fleet_documents SET superseded_at = now()"));
    }

    @Test
    void elHistorialDeMejorasEsDelTransportistaYLasBitacorasSonDelSistema() {
        var b = Flows.bidding("RLS Mejora");
        as(b.carrier()).body(Map.of("amount", b.bidAmount() - 20)).post("/api/v1/bids/" + b.bidId() + "/improve")
                .then().statusCode(200);
        assertEquals(1, countAs(b.carrier(), "CARRIER", "SELECT count(*) FROM bid_revisions"));
        assertEquals(0, countAs(b.pyme(), "PYME", "SELECT count(*) FROM bid_revisions"), "la PYME no ve montos previos");
        assertEquals(0, countAs(register("CARRIER", "RLS Mejora Otro"), "CARRIER", "SELECT count(*) FROM bid_revisions"));
        assertThrows(PersistenceException.class, () -> executeAs(b.carrier(), "CARRIER", "DELETE FROM bid_revisions"));

        // Bitácoras de avisos: la app no las lee aunque haya filas (RLS sin políticas)
        Session carrier = register("CARRIER", "RLS Bitacora");
        vehicle(carrier, plate(), 8000);
        as(carrier).body(Map.of("name", "Todo", "originCity", "LIMA")).post("/api/v1/saved-searches").then().statusCode(201);
        publishRoute(register("PYME", "RLS Bitacora Pyme"), "LIMA", "ICA", 500, 20).statusCode(201);
        assertTrue(countAsOwner("SELECT count(*) FROM saved_search_alerts") >= 1);
        assertEquals(0, countAs(carrier, "CARRIER", "SELECT count(*) FROM saved_search_alerts"));
        assertEquals(0, countAs(carrier, "CARRIER", "SELECT count(*) FROM fleet_document_notices"));
    }

    @Test
    void elHistorialDePreciosNuncaTraeGruposConMenosDeCincoMuestras() {
        // Sea cual sea el estado de la base, la vista ya filtra: ningún grupo con k < 5 ni con menos de 3 transportistas
        assertEquals(0, countAs(register("CARRIER", "RLS Lane"), "CARRIER",
                "SELECT count(*) FROM lane_price_stats WHERE samples < 5 OR carriers < 3"));
        assertEquals(0, countAsOwner("SELECT count(*) FROM lane_price_stats WHERE samples < 5 OR carriers < 3"));
    }
}
