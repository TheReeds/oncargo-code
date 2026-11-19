package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** El cotizador público limita por IP (con el límite bajado a 3 en este perfil de prueba): 429 con Retry-After. */
@QuarkusTest
@TestProfile(PublicQuoteRateLimitFlowTest.LowLimit.class)
class PublicQuoteRateLimitFlowTest {

    public static class LowLimit implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("logyx.rate-limit.public-quote.per-ip", "3");
        }
    }

    private static io.restassured.response.ValidatableResponse quote() {
        return given().contentType("application/json")
                .body(Map.of("originCity", "LIMA", "destinationCity", "CUSCO", "weightKg", 1500, "cargoType", "GENERAL"))
                .post("/api/v1/public/pricing/quote").then();
    }

    @Test
    void despuesDelLimiteResponde429ConRetryAfter() {
        for (int i = 0; i < 3; i++) quote().statusCode(200);
        quote().statusCode(429).body("error.code", is("LOGYX-AUTH-429")).header("Retry-After", notNullValue());
        // Las demás rutas públicas no comparten ese contador
        given().get("/api/v1/public/pricing/commission").then().statusCode(200);
    }
}
