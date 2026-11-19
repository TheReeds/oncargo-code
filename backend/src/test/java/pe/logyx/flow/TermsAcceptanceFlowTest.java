package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Aceptación obligatoria de los Términos y condiciones al registrarse, con versión y fecha (V18). */
@QuarkusTest
class TermsAcceptanceFlowTest {

    @Inject SystemTx systemTx;
    @Inject EntityManager em;

    private static Map<String, Object> body() {
        return new HashMap<>(Map.of("organizationType", "PYME", "legalName", "Terminos SAC", "ruc", Api.newRuc(),
                "fullName", "Admin Términos", "email", "terminos" + System.nanoTime() + "@test.pe",
                "password", "Secreta123", "acceptPrivacyPolicy", true, "acceptTerms", true));
    }

    private static io.restassured.response.ValidatableResponse register(Map<String, Object> b) {
        return given().contentType("application/json").body(b).post("/api/v1/auth/register").then();
    }

    @Test
    void sinAceptarLosTerminosNoHayCuenta() {
        Map<String, Object> b = body();
        b.put("acceptTerms", false);
        register(b).statusCode(400).body("error.code", is("LOGYX-VAL-001")).body("error.field", is("acceptTerms"));
        b.remove("acceptTerms");
        register(b).statusCode(400).body("error.field", is("acceptTerms"));
        // y no quedó cuenta: el mismo correo y RUC siguen libres
        b.put("acceptTerms", true);
        register(b).statusCode(201);
    }

    @Test
    void guardaLaVersionVigenteYLaFechaComoEvidencia() {
        String version = given().get("/api/v1/public/legal").then().statusCode(200).extract().path("data.termsVersion");
        Map<String, Object> b = body();
        b.put("termsVersion", version);
        String orgId = register(b).statusCode(201).extract().path("data.user.organizationId");

        Object[] row = systemTx.call(() -> (Object[]) em.createNativeQuery(
                "SELECT terms_version, accepted_at, source, profile_id FROM terms_acceptances WHERE organization_id = ?1")
                .setParameter(1, UUID.fromString(orgId)).getSingleResult());
        assertEquals(version, row[0]);
        assertNotNull(row[1]);
        assertEquals("REGISTER", row[2]);

        long audited = systemTx.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM audit_log WHERE organization_id = ?1 AND action = 'REGISTER' AND detail LIKE ?2")
                .setParameter(1, UUID.fromString(orgId)).setParameter(2, "%" + version + "%").getSingleResult()).longValue());
        assertEquals(1, audited);
    }

    @Test
    void sinVersionSeAsumeLaVigenteYUnaDistintaSeRechaza() {
        String orgId = register(body()).statusCode(201).extract().path("data.user.organizationId");
        long n = systemTx.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM terms_acceptances WHERE organization_id = ?1")
                .setParameter(1, UUID.fromString(orgId)).getSingleResult()).longValue());
        assertEquals(1, n);

        Map<String, Object> old = body();
        old.put("termsVersion", "1999-01-01");
        register(old).statusCode(409).body("error.code", is("LOGYX-AUTH-012")).body("error.field", is("termsVersion"));
        // el registro rechazado no dejó organización ni aceptación
        long rucs = systemTx.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM organizations WHERE ruc = ?1").setParameter(1, old.get("ruc")).getSingleResult()).longValue());
        assertEquals(0, rucs);
    }
}
