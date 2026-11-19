package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Higiene de flota (roadmap 1.12, V20): editar y archivar vehículos, licencia única, no desactivar con envíos activos. */
@QuarkusTest
class FleetHygieneFlowTest {

    @Test
    void corregirVehiculoSinCompromisosYArchivarlo() {
        Session carrier = register("CARRIER", "Higiene Carrier");
        String v = vehicle(carrier, plate(), 8000);
        as(carrier).body(Map.of("type", "TRUCK_LARGE", "maxWeightKg", 12000, "maxVolumeM3", 40))
                .patch("/api/v1/vehicles/" + v).then().statusCode(200)
                .body("data.type", is("TRUCK_LARGE")).body("data.maxWeightKg", num(12000))
                .body("data.maxVolumeM3", num(40));
        as(carrier).body(Map.of("maxWeightKg", 50)).patch("/api/v1/vehicles/" + v).then().statusCode(400);

        as(carrier).post("/api/v1/vehicles/" + v + "/archive").then().statusCode(200);
        as(carrier).get("/api/v1/vehicles").then().body("data.id", not(hasItem(v)));
        as(carrier).body(Map.of("maxVolumeM3", 10)).patch("/api/v1/vehicles/" + v).then().statusCode(409)
                .body("error.code", is("LOGYX-FLT-004"));
        // Un archivado no oferta ni sale en la elegibilidad
        Session pyme = register("PYME", "Higiene Pyme");
        String r = publishRoute(pyme, "LIMA", "JULIACA", 1000, 20).statusCode(201).extract().path("data.id");
        float floor = as(carrier).get("/api/v1/shipment-requests/" + r).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 10))
                .post("/api/v1/shipment-requests/" + r + "/bids").then().statusCode(409)
                .body("error.code", is("LOGYX-CAP-002"));
        as(carrier).get("/api/v1/shipment-requests/" + r + "/vehicle-eligibility").then()
                .body("data.vehicles", hasSize(0));
    }

    @Test
    void conEnvioActivoOOfertaPendienteSoloSeCorrigeElVolumenYNoSeArchivaNiSePoneEnMantenimiento() {
        var a = Flows.awarded("Higiene2");                  // envío PENDING del vehículo
        Session carrier = a.carrier();
        String v = as(carrier).get("/api/v1/vehicles").then().extract().path("data[0].id");
        as(carrier).body(Map.of("maxWeightKg", 9000)).patch("/api/v1/vehicles/" + v).then().statusCode(409)
                .body("error.code", is("LOGYX-FLT-004"));
        as(carrier).body(Map.of("type", "VAN")).patch("/api/v1/vehicles/" + v).then().statusCode(409);
        as(carrier).body(Map.of("maxVolumeM3", 30)).patch("/api/v1/vehicles/" + v).then().statusCode(200)
                .body("data.maxVolumeM3", num(30)).body("data.maxWeightKg", num(8000));
        as(carrier).post("/api/v1/vehicles/" + v + "/archive").then().statusCode(409)
                .body("error.code", is("LOGYX-FLT-004"));
        as(carrier).body(Map.of("status", "MAINTENANCE")).patch("/api/v1/vehicles/" + v + "/status").then()
                .statusCode(409).body("error.code", is("LOGYX-FLT-004"));
        // «En ruta» no se marca a mano (lo controla el envío)
        as(carrier).body(Map.of("status", "IN_ROUTE")).patch("/api/v1/vehicles/" + v + "/status").then().statusCode(409);

        // Con solo una oferta pendiente tampoco se archiva
        Session c2 = register("CARRIER", "Higiene Oferta");
        String v2 = vehicle(c2, plate(), 8000);
        Session pyme = register("PYME", "Higiene Oferta Pyme");
        String r = publishRoute(pyme, "LIMA", "JULIACA", 1000, 25).statusCode(201).extract().path("data.id");
        String bidId = bid(c2, r, v2, 10);
        as(c2).post("/api/v1/vehicles/" + v2 + "/archive").then().statusCode(409).body("error.code", is("LOGYX-FLT-004"));
        as(c2).post("/api/v1/bids/" + bidId + "/withdraw").then().statusCode(200);
        as(c2).post("/api/v1/vehicles/" + v2 + "/archive").then().statusCode(200);
    }

    @Test
    void conUnViajePublicadoNoSeArchiva() {
        Session carrier = register("CARRIER", "Higiene Viaje");
        String v = vehicle(carrier, plate(), 8000);
        publishTrip(carrier, v, 5000, "PER_KG", 0.5, 5).statusCode(201);
        as(carrier).post("/api/v1/vehicles/" + v + "/archive").then().statusCode(409).body("error.code", is("LOGYX-FLT-004"));
    }

    @Test
    void laLicenciaEsUnicaDentroDeLaOrganizacionPeroNoEntreOrganizaciones() {
        Session c1 = register("CARRIER", "Licencia Uno");
        Session c2 = register("CARRIER", "Licencia Dos");
        Map<String, Object> body = Map.of("fullName", "Chofer Uno", "email", "lic1" + System.nanoTime() + "@test.pe",
                "password", "Chofer1234", "licenseNumber", "q55667788");
        as(c1).body(body).post("/api/v1/drivers").then().statusCode(201);
        as(c1).body(Map.of("fullName", "Chofer Dos", "email", "lic2" + System.nanoTime() + "@test.pe",
                        "password", "Chofer1234", "licenseNumber", " Q55667788 ")).post("/api/v1/drivers").then()
                .statusCode(409).body("error.code", is("LOGYX-FLT-005")).body("error.field", is("licenseNumber"));
        as(c2).body(Map.of("fullName", "Chofer Tres", "email", "lic3" + System.nanoTime() + "@test.pe",
                "password", "Chofer1234", "licenseNumber", "Q55667788")).post("/api/v1/drivers").then().statusCode(201);
    }

    @Test
    void unConductorConEnvioActivoNoSeDesactivaHastaReasignarlo() {
        var a = Flows.awarded("Higiene3");                  // el conductor está asignado a un envío PENDING
        Session carrier = a.carrier();
        String driverId = as(carrier).get("/api/v1/drivers").then().extract().path("data[0].id");
        as(carrier).post("/api/v1/drivers/" + driverId + "/deactivate").then().statusCode(409)
                .body("error.code", is("LOGYX-FLT-006")).body("error.message", containsString("reasigna"));
        as(carrier).get("/api/v1/drivers").then().body("data[0].active", is(true));
        // Con otro conductor asignado, el primero ya se puede desactivar
        String[] relief = driver(carrier, "Chofer Relevo Higiene");
        as(carrier).body(Map.of("driverId", relief[0])).put("/api/v1/shipments/" + a.shipmentId() + "/driver")
                .then().statusCode(200);
        as(carrier).post("/api/v1/drivers/" + driverId + "/deactivate").then().statusCode(200)
                .body("data.active", is(false));
    }
}
