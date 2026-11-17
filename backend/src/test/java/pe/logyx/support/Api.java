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

    /**
     * Registra y deja la organización verificada (RN01): en test {@code logyx.verification.required=true}, así que
     * sin esto no podría publicar ni ofertar. Para probar la bandeja del operador usa {@link #registerPending}.
     */
    public static Session register(String type, String name) {
        Session s = registerPending(type, name);
        markVerified(s.orgId());
        return s;
    }

    /** Marca verificada como dueño del esquema (SystemTx), igual que lo haría el operador. */
    public static void markVerified(String orgId) {
        var system = io.quarkus.arc.Arc.container().instance(pe.logyx.shared.tenancy.SystemTx.class).get();
        var em = io.quarkus.arc.Arc.container().instance(jakarta.persistence.EntityManager.class).get();
        system.run(() -> em.createNativeQuery("UPDATE organizations SET verification_status = 'VERIFIED', "
                        + "verification_updated_at = now() WHERE id = ?1")
                .setParameter(1, java.util.UUID.fromString(orgId)).executeUpdate());
    }

    /** Registro tal cual: la organización queda PENDING de verificación. */
    public static Session registerPending(String type, String name) {
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

    /** Vehículo de un tipo dado ({@code m3} null = sin volumen declarado). */
    public static String vehicleOf(Session carrier, String type, int maxKg, Integer m3) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("plate", plate(), "type", type, "maxWeightKg", maxKg));
        if (m3 != null) body.put("maxVolumeM3", m3);
        return as(carrier).body(body).post("/api/v1/vehicles").then().statusCode(201).extract().path("data.id");
    }

    /** Solicitud LIMA → JULIACA con tipo de carga, volumen opcional y recojo en {@code pickupInDays}. */
    public static ValidatableResponse publishCargo(Session pyme, String cargoType, int weightKg, Integer m3,
                                                   int pickupInDays) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "originCity", "LIMA", "originAddress", "Av. Argentina 1234, Callao",
                "destinationCity", "JULIACA", "destinationAddress", "Jr. San Román 456",
                "cargoType", cargoType, "cargoDescription", "Carga de prueba",
                "weightKg", weightKg, "pickupDate", LocalDate.now().plusDays(pickupInDays).toString()));
        if (m3 != null) body.put("volumeM3", m3);
        return as(pyme).body(body).post("/api/v1/shipment-requests").then();
    }

    /** Oferta al monto entero inmediato superior al piso + {@code delta}; devuelve el id. */
    public static String bid(Session carrier, String requestId, String vehicleId, int delta) {
        float floor = as(carrier).get("/api/v1/shipment-requests/" + requestId).then().statusCode(200)
                .extract().path("data.priceFloor");
        return as(carrier).body(Map.of("vehicleId", vehicleId, "amount", (float) Math.ceil(floor) + delta))
                .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201)
                .extract().path("data.id");
    }

    /** Alta de conductor; devuelve {id, email}. */
    public static String[] driver(Session carrier, String name) {
        String email = "chofer" + System.nanoTime() + "@test.pe";
        String id = as(carrier).body(Map.of("fullName", name, "email", email, "password", "Chofer1234",
                "licenseNumber", "Q" + (10000000 + SEQ.incrementAndGet()))).post("/api/v1/drivers").then().statusCode(201)
                .extract().path("data.id");
        return new String[]{id, email};
    }

    public static ValidatableResponse publish(Session pyme, int weightKg) {
        return as(pyme).body(Map.of(
                        "originCity", "LIMA", "originAddress", "Av. Argentina 1234, Callao",
                        "destinationCity", "JULIACA", "destinationAddress", "Jr. San Román 456",
                        "cargoType", "GENERAL", "cargoDescription", "Abarrotes en cajas",
                        "weightKg", weightKg, "pickupDate", LocalDate.now().plusDays(2).toString()))
                .post("/api/v1/shipment-requests").then();
    }

    /** Publica una solicitud con ruta y fecha de recojo propias (carga general). */
    public static ValidatableResponse publishRoute(Session pyme, String from, String to, int weightKg, int pickupInDays) {
        return as(pyme).body(Map.of(
                        "originCity", from, "originAddress", "Av. Origen 100",
                        "destinationCity", to, "destinationAddress", "Jr. Destino 200",
                        "cargoType", "GENERAL", "cargoDescription", "Mercadería variada",
                        "weightKg", weightKg, "pickupDate", LocalDate.now().plusDays(pickupInDays).toString()))
                .post("/api/v1/shipment-requests").then();
    }

    /** Salida ISO-8601 dentro de {@code days} días. */
    public static String departure(int days) {
        return java.time.OffsetDateTime.now().plusDays(days).withNano(0).toString();
    }

    /** Publica un viaje LIMA → JULIACA. */
    public static ValidatableResponse publishTrip(Session carrier, String vehicleId, int capacityKg, String mode,
                                                  double rate, int departInDays) {
        return as(carrier).body(Map.of("originCity", "LIMA", "destinationCity", "JULIACA",
                        "departureAt", departure(departInDays), "vehicleId", vehicleId, "capacityKg", capacityKg,
                        "pricingMode", mode, "rate", rate, "notes", "Salida puntual"))
                .post("/api/v1/trips").then();
    }

    public static ValidatableResponse bookTrip(Session pyme, String tripId, int kg) {
        return as(pyme).body(Map.of("weightKg", kg, "cargoDescription", "Cajas de abarrotes", "cargoType", "GENERAL",
                        "pickupAddress", "Av. Argentina 1234, Callao", "deliveryAddress", "Jr. San Román 456, Juliaca"))
                .post("/api/v1/trips/" + tripId + "/bookings").then();
    }

    /** Compara un número JSON sin importar si RestAssured lo entrega como Integer, Float o Double. */
    public static org.hamcrest.Matcher<Object> num(double expected) {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Object actual) {
                return actual instanceof Number n && Math.abs(n.doubleValue() - expected) < 0.005;
            }

            @Override
            public void describeTo(org.hamcrest.Description d) {
                d.appendText("number ").appendValue(expected);
            }
        };
    }

    /** Sin Content-Type JSON: para multipart y descargas. */
    public static RequestSpecification raw(Session s) {
        return given().auth().oauth2(s.token());
    }

    /** Imagen PNG mínima (firma binaria válida). */
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};
    public static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes();

    /** Sube un archivo al envío (multipart). */
    public static ValidatableResponse upload(Session s, String shipmentId, String type, String stopId,
                                             String fileName, byte[] content, String mime) {
        var spec = raw(s).multiPart("file", fileName, content, mime).multiPart("type", type);
        if (stopId != null) spec = spec.multiPart("stopId", stopId);
        return spec.post("/api/v1/shipments/" + shipmentId + "/documents").then();
    }

    /** Foto de evidencia en la parada de entrega (requisito para completarla). */
    public static void deliveryPhoto(Session driver, String shipmentId, String deliveryStopId) {
        upload(driver, shipmentId, "DELIVERY_PHOTO", deliveryStopId, "entrega.png", PNG, "image/png").statusCode(201);
    }

    public static String plate() {
        int n = SEQ.incrementAndGet() % 1000;
        char a = (char) ('A' + n % 26), b = (char) ('A' + (n / 26) % 26);
        return "T" + a + b + "-" + String.format("%03d", n);
    }
}
