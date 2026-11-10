package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Adiciones al contrato de 2026-11-10 (ver docs/api/CHANGELOG.md). */
@QuarkusTest
class ContractAdditionsFlowTest {

    private static String publishRoute(Session pyme, String from, String to, String cargo, int kg, int daysAhead) {
        return as(pyme).body(Map.of(
                        "originCity", from, "originAddress", "Av. Origen 100",
                        "destinationCity", to, "destinationAddress", "Jr. Destino 200",
                        "cargoType", cargo, "cargoDescription", "Carga de prueba",
                        "weightKg", kg, "pickupDate", LocalDate.now().plusDays(daysAhead).toString()))
                .post("/api/v1/shipment-requests").then().statusCode(201).extract().path("data.id");
    }

    @Test
    void ofertaExponeVehiculoYSolicitudExponeRucYVehiculoMinimo() {
        Session pyme = register("PYME", "Ferreteria Cusco SAC");
        Session carrier = register("CARRIER", "Carga Andina SAC");
        String plate = plate();
        String veh = vehicle(carrier, plate, 9000);
        String id = publishRoute(pyme, "LIMA", "JULIACA", "PERISHABLE", 2000, 3);

        as(carrier).get("/api/v1/shipment-requests/" + id).then().statusCode(200)
                .body("data.requesterRucVerified", is(true))
                .body("data.minVehicleType", is("REFRIGERATED"));
        float price = as(pyme).get("/api/v1/shipment-requests/" + id).then().extract().path("data.priceFloor");

        // La respuesta del POST ya trae tipo y placa
        as(carrier).body(Map.of("vehicleId", veh, "amount", price + 50))
                .post("/api/v1/shipment-requests/" + id + "/bids").then().statusCode(201)
                .body("data.vehicleType", is("TRUCK_MEDIUM"))
                .body("data.vehiclePlate", is(plate));

        // La PYME no lee la flota (RLS) pero ve tipo y placa en la oferta
        as(pyme).get("/api/v1/shipment-requests/" + id + "/bids").then().statusCode(200)
                .body("data[0].vehicleType", is("TRUCK_MEDIUM"))
                .body("data[0].vehiclePlate", is(plate));
        as(pyme).get("/api/v1/vehicles").then().statusCode(403);
    }

    @Test
    void userInfoIncluyeCiudadDeLaOrganizacion() {
        Session s = register("PYME", "Ciudad SAC");
        as(s).get("/api/v1/auth/me").then().statusCode(200).body("data.organizationCity", is("Juliaca"));
        given().contentType("application/json").body(Map.of("email", s.email(), "password", "Secreta123"))
                .post("/api/v1/auth/login").then().statusCode(200).body("data.user.organizationCity", is("Juliaca"));
    }

    @Test
    void erroresDeNegocioIndicanElCampo() {
        Session pyme = register("PYME", "Campos SAC");
        Session carrier = register("CARRIER", "Campos Carga SAC");
        String small = vehicle(carrier, plate(), 1000);
        var req = as(pyme).get("/api/v1/shipment-requests/" + publishRoute(pyme, "LIMA", "JULIACA", "GENERAL", 4000, 2))
                .then().extract();
        String id = req.path("data.id");
        float floor = req.path("data.priceFloor");

        // vehículo sin capacidad → vehicleId
        as(carrier).body(Map.of("vehicleId", small, "amount", floor + 10))
                .post("/api/v1/shipment-requests/" + id + "/bids").then().statusCode(400)
                .body("error.code", is("LOGYX-CAP-001")).body("error.field", is("vehicleId"));

        // bajo el piso → amount, con formato S/ 1,234.56
        String big = vehicle(carrier, plate(), 9000);
        var low = as(carrier).body(Map.of("vehicleId", big, "amount", 1))
                .post("/api/v1/shipment-requests/" + id + "/bids").then().statusCode(400)
                .body("error.code", is("LOGYX-BID-001")).body("error.field", is("amount")).extract();
        String msg = low.path("error.message");
        org.junit.jupiter.api.Assertions.assertTrue(msg.matches(".*S/ \\d{1,3}(,\\d{3})*\\.\\d{2}.*"), msg);

        // RUC inválido → ruc ; RUC repetido → ruc ; email repetido → email
        Map<String, Object> body = new HashMap<>(Map.of(
                "organizationType", "PYME", "legalName", "Dup SAC", "ruc", "20100070971",
                "fullName", "X", "email", "dup" + System.nanoTime() + "@test.pe", "password", "Secreta123",
                "acceptPrivacyPolicy", true));
        given().contentType("application/json").body(body).post("/api/v1/auth/register").then().statusCode(400)
                .body("error.code", is("LOGYX-ORG-001")).body("error.field", is("ruc"));

        String ruc = Api.newRuc();
        body.put("ruc", ruc);
        given().contentType("application/json").body(body).post("/api/v1/auth/register").then().statusCode(201);
        body.put("email", "otro" + System.nanoTime() + "@test.pe");
        given().contentType("application/json").body(body).post("/api/v1/auth/register").then().statusCode(409)
                .body("error.code", is("LOGYX-ORG-002")).body("error.field", is("ruc"));

        body.put("ruc", Api.newRuc());
        body.put("email", pyme.email());
        given().contentType("application/json").body(body).post("/api/v1/auth/register").then().statusCode(409)
                .body("error.code", is("LOGYX-AUTH-002")).body("error.field", is("email"));

        // email de conductor repetido → email ; placa repetida → plate
        as(carrier).body(Map.of("fullName", "Pedro", "email", pyme.email(), "password", "Chofer1234",
                        "licenseNumber", "Q1"))
                .post("/api/v1/drivers").then().statusCode(409).body("error.field", is("email"));
        String plate = plate();
        vehicle(carrier, plate, 5000);
        as(carrier).body(Map.of("plate", plate, "type", "VAN", "maxWeightKg", 900))
                .post("/api/v1/vehicles").then().statusCode(409)
                .body("error.code", is("LOGYX-FLT-001")).body("error.field", is("plate"));
    }

    @Test
    void marketplaceFiltraOrdenaYPagina() {
        Session pyme = register("PYME", "Paginas SAC");
        Session carrier = register("CARRIER", "Paginas Carga SAC");
        // Ruta exclusiva (ABANCAY → AYACUCHO) para aislar de otros tests
        String a = publishRoute(pyme, "ABANCAY", "AYACUCHO", "GENERAL", 500, 5);
        String b = publishRoute(pyme, "ABANCAY", "AYACUCHO", "FRAGILE", 7000, 3);
        String c = publishRoute(pyme, "ABANCAY", "AYACUCHO", "GENERAL", 2000, 4);
        String base = "/api/v1/shipment-requests?originCity=ABANCAY&destinationCity=AYACUCHO";

        // Por omisión: recojo más próximo primero + meta de paginación
        as(carrier).get(base).then().statusCode(200)
                .body("data.id", contains(b, c, a))
                .body("meta.page", is(0)).body("meta.size", is(20)).body("meta.total", is(3));

        as(carrier).get(base + "&sort=recent").then().body("data.id", contains(c, b, a));
        as(carrier).get(base + "&sort=price_desc").then().body("data.id", contains(b, c, a));
        as(carrier).get(base + "&maxWeightKg=2000").then().body("data.id", contains(c, a)).body("meta.total", is(2));
        as(carrier).get(base + "&cargoType=FRAGILE").then().body("data.id", contains(b));
        as(carrier).get(base + "&pickupFrom=" + LocalDate.now().plusDays(4) + "&pickupTo=" + LocalDate.now().plusDays(5))
                .then().body("data.id", contains(c, a));
        as(carrier).get(base + "&size=2&page=1").then().body("data.id", contains(a))
                .body("meta.page", is(1)).body("meta.size", is(2)).body("meta.total", is(3));
        as(carrier).get("/api/v1/shipment-requests?originCity=LIMA&destinationCity=TACNA&cargoType=BULK&maxWeightKg=1")
                .then().body("data", empty()).body("meta.total", is(0));

        // Parámetros inválidos → 400 con field
        as(carrier).get(base + "&sort=nope").then().statusCode(400).body("error.field", is("sort"));
        as(carrier).get(base + "&size=101").then().statusCode(400).body("error.field", is("size"));
        as(carrier).get("/api/v1/shipment-requests?originCity=MARTE").then().statusCode(400).body("error.field", is("originCity"));

        as(carrier).get(base + "&pickupFrom=ayer").then().statusCode(400);

        // /mine: sin parámetros no hay meta de página; con ellos sí
        as(pyme).get("/api/v1/shipment-requests/mine").then().statusCode(200)
                .body("data", hasSize(3)).body("meta.total", nullValue());
        as(pyme).get("/api/v1/shipment-requests/mine?size=2&page=1").then().statusCode(200)
                .body("data", hasSize(1)).body("meta.total", is(3)).body("meta.page", is(1));
    }

    @Test
    void envioActivoDelConductorYDashboards() {
        Session pyme = register("PYME", "Dash PYME SAC");
        Session carrier = register("CARRIER", "Dash Carga SAC");
        String veh = vehicle(carrier, plate(), 8000);
        String driverEmail = "chofer" + System.nanoTime() + "@test.pe";
        String driverId = as(carrier).body(Map.of("fullName", "Luis Mamani", "email", driverEmail,
                        "password", "Chofer1234", "licenseNumber", "Q99999999"))
                .post("/api/v1/drivers").then().statusCode(201).extract().path("data.id");
        Session driver = login(driverEmail, "Chofer1234");

        // Sin envíos: 404 con código propio
        as(driver).get("/api/v1/driver/shipments/active").then().statusCode(404)
                .body("error.code", is("LOGYX-SHP-002"));
        // Dashboards vacíos
        as(pyme).get("/api/v1/dashboard/summary").then().statusCode(200)
                .body("data.role", is("PYME")).body("data.activeRequests", is(0))
                .body("data.bidsToReview", is(0)).body("data.shipmentsInTransit", is(0))
                .body("data.deliveredThisMonth", is(0)).body("data.compatibleLoads", nullValue());
        as(carrier).get("/api/v1/dashboard/summary").then().statusCode(200)
                .body("data.role", is("CARRIER")).body("data.pendingBids", is(0))
                .body("data.activeShipments", is(0)).body("data.activeRequests", nullValue());
        as(driver).get("/api/v1/dashboard/summary").then().statusCode(403);

        String id = publishRoute(pyme, "LIMA", "JULIACA", "GENERAL", 3000, 2);
        float floor = as(pyme).get("/api/v1/shipment-requests/" + id).then().extract().path("data.priceFloor");
        as(pyme).get("/api/v1/dashboard/summary").then().body("data.activeRequests", is(1));
        as(carrier).get("/api/v1/dashboard/summary").then().body("data.compatibleLoads", greaterThanOrEqualTo(1));

        String bid = as(carrier).body(Map.of("vehicleId", veh, "amount", floor + 10))
                .post("/api/v1/shipment-requests/" + id + "/bids").then().statusCode(201).extract().path("data.id");
        as(pyme).get("/api/v1/dashboard/summary").then().body("data.bidsToReview", is(1));
        as(carrier).get("/api/v1/dashboard/summary").then().body("data.pendingBids", is(1));

        String shipmentId = as(pyme).post("/api/v1/bids/" + bid + "/accept").then().statusCode(200)
                .extract().path("data.shipmentId");
        as(carrier).body(Map.of("driverId", driverId)).put("/api/v1/shipments/" + shipmentId + "/driver")
                .then().statusCode(200);
        as(pyme).get("/api/v1/dashboard/summary").then()
                .body("data.activeRequests", is(0)).body("data.bidsToReview", is(0));
        as(carrier).get("/api/v1/dashboard/summary").then()
                .body("data.activeShipments", is(1)).body("data.pendingBids", is(0));

        // Envío PENDING asignado → es el activo
        as(driver).get("/api/v1/driver/shipments/active").then().statusCode(200)
                .body("data.id", is(shipmentId)).body("data.status", is("PENDING"));

        List<String> stops = as(driver).get("/api/v1/driver/shipments/active").then().extract().path("data.stops.id");
        String path = "/api/v1/shipments/" + shipmentId + "/stops/";
        as(driver).post(path + stops.get(0) + "/arrive").then().statusCode(200);
        as(driver).post(path + stops.get(0) + "/complete").then().statusCode(200);
        as(driver).get("/api/v1/driver/shipments/active").then().body("data.status", is("IN_TRANSIT"));
        as(pyme).get("/api/v1/dashboard/summary").then().body("data.shipmentsInTransit", is(1));

        as(driver).post(path + stops.get(1) + "/arrive").then().statusCode(200);
        as(driver).post(path + stops.get(1) + "/complete").then().statusCode(200);
        as(driver).get("/api/v1/driver/shipments/active").then().statusCode(404);
        as(pyme).get("/api/v1/dashboard/summary").then()
                .body("data.shipmentsInTransit", is(0)).body("data.deliveredThisMonth", is(1));
        as(carrier).get("/api/v1/dashboard/summary").then()
                .body("data.activeShipments", is(0)).body("data.deliveredThisMonth", is(1));

        // /shipments paginado opcional
        as(pyme).get("/api/v1/shipments").then().body("data", hasSize(1)).body("meta.total", nullValue());
        as(pyme).get("/api/v1/shipments?page=0&size=1").then().body("data", hasSize(1)).body("meta.total", is(1));
        as(pyme).get("/api/v1/shipments?page=1&size=1").then().body("data", empty()).body("meta.total", is(1));
    }
}
