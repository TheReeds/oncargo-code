package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Rate limiting de login (docs/security.md): tras 10 fallos la cuenta se bloquea temporalmente con 429. */
@QuarkusTest
class AuthRateLimitFlowTest {

    private static io.restassured.response.ValidatableResponse attempt(String email, String password) {
        return given().contentType("application/json").body(Map.of("email", email, "password", password))
                .post("/api/v1/auth/login").then();
    }

    @Test
    void diezFallosBloqueanLaCuentaAunqueLaContrasenaSeaCorrecta() {
        String email = register("PYME", "Rate Limit").email();

        attempt(email, "Secreta123").statusCode(200);            // un login correcto no suma
        for (int i = 0; i < 10; i++) attempt(email, "incorrecta").statusCode(401).body("error.code", is("LOGYX-AUTH-001"));
        attempt(email, "Secreta123").statusCode(429).body("error.code", is("LOGYX-AUTH-429"))
                .header("Retry-After", notNullValue());
        attempt("otro" + email, "x12345678").statusCode(401);    // otra cuenta no se ve afectada
    }
}
