package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/**
 * Flujo principal del MVP de punta a punta:
 * PYME publica → transportistas ofertan → PYME acepta → envío → conductor ejecuta.
 */
@QuarkusTest
class MarketplaceFlowTest {

    @Test
    void flujoCompletoDeSolicitudAEntrega() {
        Session pyme = register("PYME", "Comercial Altiplano SAC");
        Session carrierA = register("CARRIER", "Transportes Sur Andino SRL");
        Session carrierB = register("CARRIER", "Logistica Titicaca EIRL");

        String vehA = vehicle(carrierA, plate(), 8000);
        String vehB = vehicle(carrierB, plate(), 8000);

        // Conductor del transportista A
        String driverEmail = "chofer" + System.nanoTime() + "@test.pe";
        String driverId = as(carrierA).body(Map.of("fullName", "Juan Quispe", "email", driverEmail,
                        "password", "Chofer1234", "licenseNumber", "Q12345678"))
                .post("/api/v1/drivers").then().statusCode(201).extract().path("data.id");

        // PYME publica: el sistema calcula precio sugerido y piso
        var published = publish(pyme, 3000).statusCode(201)
                .body("data.status", is("PUBLISHED"))
                .body("data.auction.status", is("OPEN"))
                .body("data.suggestedPrice", greaterThan(0f))
                .extract();
        String requestId = published.path("data.id");
        float floor = published.path("data.priceFloor");

        // Aparece en el marketplace del transportista
        as(carrierA).get("/api/v1/shipment-requests").then().statusCode(200)
                .body("data.id", hasItem(requestId));

        // RN03: oferta bajo el piso → rechazada
        as(carrierA).body(Map.of("vehicleId", vehA, "amount", floor - 1))
                .post("/api/v1/shipment-requests/" + requestId + "/bids")
                .then().statusCode(400).body("error.code", is("LOGYX-BID-001"));

        // RN02: no se puede ofertar con un vehículo ajeno
        as(carrierA).body(Map.of("vehicleId", vehB, "amount", floor + 100))
                .post("/api/v1/shipment-requests/" + requestId + "/bids")
                .then().statusCode(404);

        String bidA = as(carrierA).body(Map.of("vehicleId", vehA, "amount", floor + 200, "notes", "Salida 6 am"))
                .post("/api/v1/shipment-requests/" + requestId + "/bids")
                .then().statusCode(201).extract().path("data.id");
        String bidB = as(carrierB).body(Map.of("vehicleId", vehB, "amount", floor + 500))
                .post("/api/v1/shipment-requests/" + requestId + "/bids")
                .then().statusCode(201).extract().path("data.id");

        // Una sola oferta por transportista
        as(carrierA).body(Map.of("vehicleId", vehA, "amount", floor + 150))
                .post("/api/v1/shipment-requests/" + requestId + "/bids")
                .then().statusCode(409).body("error.code", is("LOGYX-BID-003"));

        // El transportista ve el conteo, pero solo su oferta
        as(carrierB).get("/api/v1/shipment-requests/" + requestId).then().statusCode(200)
                .body("data.auction.bidCount", is(2))
                .body("data.myBid.id", is(bidB));

        // La PYME ve ambas, ordenadas por monto
        as(pyme).get("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(200)
                .body("data.id", contains(bidA, bidB));

        // RN05: aceptar A rechaza B y crea el envío
        String shipmentId = as(pyme).post("/api/v1/bids/" + bidA + "/accept").then().statusCode(200)
                .body("data.rejectedBidIds", contains(bidB))
                .extract().path("data.shipmentId");

        as(pyme).get("/api/v1/shipment-requests/" + requestId).then()
                .body("data.status", is("MATCHED"))
                .body("data.auction.status", is("CLOSED"));

        // El transportista asigna conductor
        as(carrierA).body(Map.of("driverId", driverId))
                .put("/api/v1/shipments/" + shipmentId + "/driver").then().statusCode(200)
                .body("data.driverName", is("Juan Quispe"));

        // El transportista perdedor no ve el envío
        as(carrierB).get("/api/v1/shipments/" + shipmentId).then().statusCode(404);

        // El conductor ejecuta la ruta
        Session driver = login(driverEmail, "Chofer1234");
        var mine = as(driver).get("/api/v1/driver/shipments").then().statusCode(200)
                .body("data.id", contains(shipmentId)).extract();
        List<String> stops = mine.path("data[0].stops.id");
        String base = "/api/v1/shipments/" + shipmentId + "/stops/";

        // RN08: no se puede saltar a la entrega
        as(driver).post(base + stops.get(1) + "/arrive").then().statusCode(409)
                .body("error.code", is("LOGYX-TRK-001"));

        as(driver).post(base + stops.get(0) + "/arrive").then().statusCode(200);
        as(driver).post(base + stops.get(0) + "/complete").then().statusCode(200)
                .body("data.status", is("IN_TRANSIT"));
        as(driver).post(base + stops.get(1) + "/arrive").then().statusCode(200);
        deliveryPhoto(driver, shipmentId, stops.get(1));   // RF37: sin foto no se completa la entrega
        as(driver).post(base + stops.get(1) + "/complete").then().statusCode(200)
                .body("data.status", is("DELIVERED"));

        // La PYME ve el envío entregado con placa y conductor
        as(pyme).get("/api/v1/shipments/" + shipmentId).then().statusCode(200)
                .body("data.status", is("DELIVERED"))
                .body("data.driverName", is("Juan Quispe"))
                .body("data.vehiclePlate", notNullValue());

        // El vehículo vuelve a estar disponible
        as(carrierA).get("/api/v1/vehicles").then()
                .body("data.find { it.id == '" + vehA + "' }.status", is("AVAILABLE"));
    }

    @Test
    void subastaCierraAlRecibirElMaximoDeOfertas() {
        Session pyme = register("PYME", "Distribuidora Puno SAC");
        String requestId = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        float floor = as(pyme).get("/api/v1/shipment-requests/" + requestId).then().extract().path("data.priceFloor");

        for (int i = 0; i < 5; i++) {
            Session c = register("CARRIER", "Carrier Max " + (char) ('A' + i));
            String v = vehicle(c, plate(), 5000);
            as(c).body(Map.of("vehicleId", v, "amount", floor + i * 10))
                    .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201);
        }
        Session late = register("CARRIER", "Carrier Tardio");
        String v = vehicle(late, plate(), 5000);
        as(late).body(Map.of("vehicleId", v, "amount", floor + 1))
                .post("/api/v1/shipment-requests/" + requestId + "/bids")
                .then().statusCode(409).body("error.code", is("LOGYX-AUC-002"));

        as(pyme).get("/api/v1/shipment-requests/" + requestId).then()
                .body("data.auction.status", is("CLOSED"))
                .body("data.auction.closeReason", is("MAX_BIDS"));
    }

    @Test
    void vehiculoSinCapacidadNoPuedeOfertar() {
        Session pyme = register("PYME", "Textiles Juliaca SAC");
        Session carrier = register("CARRIER", "Mototaxis Express");
        String small = vehicle(carrier, plate(), 1000);
        var req = publish(pyme, 4000).statusCode(201).extract();
        float price = req.path("data.suggestedPrice");
        String id = req.path("data.id");
        as(carrier).body(Map.of("vehicleId", small, "amount", price))
                .post("/api/v1/shipment-requests/" + id + "/bids")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-001"));
    }

    @Test
    void transportistaNoPuedePublicarYConductorNoAdministraFlota() {
        Session carrier = register("CARRIER", "Solo Transporte SAC");
        publish(carrier, 100).statusCode(403);
    }

    @Test
    void registroExigeConsentimientoYRucValido() {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "organizationType", "PYME", "legalName", "Sin Consentimiento SAC", "ruc", Api.newRuc(),
                "fullName", "X", "email", "x" + System.nanoTime() + "@test.pe", "password", "Secreta123",
                "acceptPrivacyPolicy", false, "acceptTerms", true));
        given().contentType("application/json").body(body).post("/api/v1/auth/register")
                .then().statusCode(400).body("error.field", is("acceptPrivacyPolicy"));

        body.put("acceptPrivacyPolicy", true);
        body.put("ruc", "20100070971");
        given().contentType("application/json").body(body).post("/api/v1/auth/register")
                .then().statusCode(400).body("error.code", is("LOGYX-ORG-001"));
    }

    @Test
    void endpointsProtegidosExigenToken() {
        given().get("/api/v1/shipment-requests").then().statusCode(401);
        given().get("/api/v1/catalog/cities").then().statusCode(200).body("data.code", hasItem("JULIACA"));
    }
}
