package pe.logyx.support;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;

/** Helpers de test: registrar organizaciones y llamar a la API autenticado. */
public final class Api {

    private static final AtomicInteger SEQ = new AtomicInteger((int) (System.nanoTime() % 100000));

    private Api() {}

    public record Session(String token, String orgId, String email) {}

    /** Genera un RUC válido (módulo 11) y único por ejecución. */
    public static String newRuc() {
        int[] w = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        String base = "20" + String.format("%08d", SEQ.incrementAndGet() * 7919 % 100_000_000);
        int sum = 0;
        for (int i = 0; i < 10; i++) sum += (base.charAt(i) - '0') * w[i];
        int c = 11 - sum % 11;
        if (c == 10) c = 0;
        if (c == 11) c = 1;
        return base + c;
    }

    public static Session register(String type, String name) {
        String email = name.toLowerCase().replaceAll("[^a-z]", "") + SEQ.incrementAndGet() + "@test.pe";
        var body = Map.of(
                "organizationType", type, "legalName", name, "ruc", newRuc(), "city", "Juliaca",
                "fullName", "Admin " + name, "email", email, "password", "Secreta123",
                "acceptPrivacyPolicy", true);
        var r = given().contentType(ContentType.JSON).body(body)
                .post("/api/v1/auth/register").then().statusCode(201).extract();
        return new Session(r.path("data.accessToken"), r.path("data.user.organizationId"), email);
    }

    public static Session login(String email, String password) {
        var r = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                .post("/api/v1/auth/login").then().statusCode(200).extract();
        return new Session(r.path("data.accessToken"), r.path("data.user.organizationId"), email);
    }

    public static RequestSpecification as(Session s) {
        return given().auth().oauth2(s.token()).contentType(ContentType.JSON);
    }

    public static String vehicle(Session carrier, String plate, int maxKg) {
        return as(carrier).body(Map.of("plate", plate, "type", "TRUCK_MEDIUM", "maxWeightKg", maxKg))
                .post("/api/v1/vehicles").then().statusCode(201).extract().path("data.id");
    }

    public static ValidatableResponse publish(Session pyme, int weightKg) {
        return as(pyme).body(Map.of(
                        "originCity", "LIMA", "originAddress", "Av. Argentina 1234, Callao",
                        "destinationCity", "JULIACA", "destinationAddress", "Jr. San Román 456",
                        "cargoType", "GENERAL", "cargoDescription", "Abarrotes en cajas",
                        "weightKg", weightKg, "pickupDate", LocalDate.now().plusDays(2).toString()))
                .post("/api/v1/shipment-requests").then();
    }

    public static String plate() {
        int n = SEQ.incrementAndGet() % 1000;
        char a = (char) ('A' + n % 26), b = (char) ('A' + (n / 26) % 26);
        return "T" + a + b + "-" + String.format("%03d", n);
    }
}
