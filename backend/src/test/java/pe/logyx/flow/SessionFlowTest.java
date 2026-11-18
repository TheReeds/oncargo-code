package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** F02/F24/F55: cambio de contraseña, cerrar todas las sesiones y refresh tokens rotativos (V14, ADR-0009). */
@QuarkusTest
class SessionFlowTest {

    private static io.restassured.response.ExtractableResponse<io.restassured.response.Response> loginRefresh(
            String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password, "refresh", true))
                .post("/api/v1/auth/login").then().statusCode(200).extract();
    }

    private static io.restassured.response.ValidatableResponse refresh(String token) {
        return given().contentType(ContentType.JSON).body(Map.of("refreshToken", token))
                .post("/api/v1/auth/refresh").then();
    }

    @Test
    void cambiarContrasenaCierraLasDemasSesionesYDevuelveUnaNueva() {
        Session s = register("PYME", "Cambio Clave");
        Session other = login(s.email(), "Secreta123");            // otro dispositivo
        as(s).body(Map.of("currentPassword", "incorrecta", "newPassword", "NuevaClave2026"))
                .post("/api/v1/auth/change-password").then().statusCode(400)
                .body("error.code", is("LOGYX-AUTH-006"), "error.field", is("currentPassword"));
        as(s).body(Map.of("currentPassword", "Secreta123", "newPassword", "Secreta123"))
                .post("/api/v1/auth/change-password").then().statusCode(400).body("error.code", is("LOGYX-AUTH-010"));
        String fresh = as(s).body(Map.of("currentPassword", "Secreta123", "newPassword", "NuevaClave2026"))
                .post("/api/v1/auth/change-password").then().statusCode(200)
                .body("data.refreshToken", nullValue(), "data.user.email", is(s.email()))
                .extract().path("data.accessToken");
        as(s).get("/api/v1/auth/me").then().statusCode(401).body("error.code", is("LOGYX-AUTH-401"));
        as(other).get("/api/v1/auth/me").then().statusCode(401);
        given().auth().oauth2(fresh).get("/api/v1/auth/me").then().statusCode(200);
        login(s.email(), "NuevaClave2026");
    }

    @Test
    void cerrarSesionEnTodosLosDispositivos() {
        Session s = register("CARRIER", "Logout All");
        Session other = login(s.email(), "Secreta123");
        as(s).post("/api/v1/auth/logout-all").then().statusCode(204);
        as(s).get("/api/v1/auth/me").then().statusCode(401);
        as(other).get("/api/v1/vehicles").then().statusCode(401);
        login(s.email(), "Secreta123");                              // puede volver a entrar
    }

    @Test
    void elConductorPuedeCambiarSuContrasena() {
        Session carrier = register("CARRIER", "Chofer Clave");
        String[] d = driver(carrier, "Chofer Clave");
        Session driver = login(d[1], "Chofer1234");
        as(driver).body(Map.of("currentPassword", "Chofer1234", "newPassword", "ChoferNueva1"))
                .post("/api/v1/auth/change-password").then().statusCode(200).body("data.user.role", is("DRIVER"));
    }

    @Test
    void refreshRotaYLaReutilizacionRevocaLaFamilia() {
        Session s = register("CARRIER", "Refresh Rota");
        var r = loginRefresh(s.email(), "Secreta123");
        String r1 = r.path("data.refreshToken");
        int expiresIn = r.path("data.expiresIn");
        org.junit.jupiter.api.Assertions.assertEquals(3600, expiresIn);
        org.junit.jupiter.api.Assertions.assertTrue(((Number) r.path("data.refreshExpiresIn")).longValue() > 29L * 86400);
        // La web (sin refresh) no recibe refresh token
        given().contentType(ContentType.JSON).body(Map.of("email", s.email(), "password", "Secreta123"))
                .post("/api/v1/auth/login").then().statusCode(200).body("data.refreshToken", nullValue(),
                        "data.expiresIn", is(28800));

        var rotated = refresh(r1).statusCode(200).extract();
        String r2 = rotated.path("data.refreshToken");
        String access2 = rotated.path("data.accessToken");
        given().auth().oauth2(access2).get("/api/v1/auth/me").then().statusCode(200);
        String r3 = refresh(r2).statusCode(200).extract().path("data.refreshToken");

        // Reutilizar r1 (ya usado) = robo: se revoca la familia, r3 deja de valer
        refresh(r1).statusCode(401).body("error.code", is("LOGYX-AUTH-009"));
        refresh(r3).statusCode(401).body("error.code", is("LOGYX-AUTH-009"));
        refresh("basura").statusCode(401);
    }

    @Test
    void logoutRevocaLaSesionDelDispositivo() {
        Session s = register("PYME", "Refresh Logout");
        String r1 = loginRefresh(s.email(), "Secreta123").path("data.refreshToken");
        given().contentType(ContentType.JSON).body(Map.of("refreshToken", r1)).post("/api/v1/auth/logout")
                .then().statusCode(204);
        refresh(r1).statusCode(401);
        given().contentType(ContentType.JSON).post("/api/v1/auth/logout").then().statusCode(204);  // sin body
    }

    @Test
    void refreshRechazaPerfilDesactivadoOSesionRevocada() {
        Session carrier = register("CARRIER", "Refresh Chofer");
        String[] d = driver(carrier, "Refresh Chofer");
        String r1 = loginRefresh(d[1], "Chofer1234").path("data.refreshToken");
        String r2 = loginRefresh(d[1], "Chofer1234").path("data.refreshToken");
        as(carrier).post("/api/v1/drivers/" + d[0] + "/deactivate").then().statusCode(200).body("data.active", is(false));
        refresh(r1).statusCode(401);
        as(carrier).post("/api/v1/drivers/" + d[0] + "/reactivate").then().statusCode(200).body("data.active", is(true));
        refresh(r2).statusCode(401);                        // la sesión anterior a la desactivación no revive

        String r3 = loginRefresh(d[1], "Chofer1234").path("data.refreshToken");
        Session driver = login(d[1], "Chofer1234");
        as(driver).post("/api/v1/auth/logout-all").then().statusCode(204);
        refresh(r3).statusCode(401);
    }
}
