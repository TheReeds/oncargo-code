package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * RN02 por fecha de recojo (decisión del dueño, 2026-11-17): un vehículo oferta si su agenda (envíos activos y ofertas
 * pendientes, ventana recojo + ⌈km/500⌉ días) no se solapa con la carga. Aviso CAP-006 antes de ofertar, tope CAP-007,
 * mantenimiento bloquea, la validación dura queda al aceptar (CAP-005).
 */
@QuarkusTest
class VehicleAvailabilityFlowTest {

    private static String reqPath(String id) { return "/api/v1/shipment-requests/" + id; }

    private static String vehicleOf(Session carrier) {
        return as(carrier).get("/api/v1/vehicles").then().statusCode(200).extract().path("data[0].id");
    }

    private static String eligPath(String requestId) { return reqPath(requestId) + "/vehicle-eligibility"; }

    @Test
    void unVehiculoEnRutaOfertaSiSuVentanaNoSeSolapa() {
        var a = Flows.inTransit("Agenda");                       // envío Lima → Juliaca, recojo en 2 días, ocupa ~3 días
        Session carrier = a.carrier();
        String v = vehicleOf(carrier);
        as(carrier).get("/api/v1/vehicles").then().body("data[0].status", is("IN_ROUTE"));
        Session pyme = register("PYME", "Agenda Pyme");
        String near = publishRoute(pyme, "LIMA", "JULIACA", 1000, 3).statusCode(201).extract().path("data.id");
        String far = publishRoute(pyme, "LIMA", "JULIACA", 1000, 30).statusCode(201).extract().path("data.id");

        // Ventana solapada: no elegible, con el impedimento y desde cuándo queda libre
        as(carrier).get(eligPath(near)).then().statusCode(200)
                .body("data.vehicles", hasSize(1))
                .body("data.vehicles[0].vehicleId", is(v))
                .body("data.vehicles[0].eligible", is(false))
                .body("data.vehicles[0].blockerCode", is("LOGYX-CAP-005"))
                .body("data.vehicles[0].freeFrom", notNullValue())
                .body("data.vehicles[0].busy[0].kind", is("SHIPMENT"));
        // Ventana libre: elegible aunque hoy esté en ruta
        as(carrier).get(eligPath(far)).then().statusCode(200)
                .body("data.vehicles[0].eligible", is(true))
                .body("data.vehicles[0].status", is("IN_ROUTE"))
                .body("data.vehicles[0].blockerCode", nullValue())
                .body("data.vehicles[0].busy", hasSize(0))
                .body("data.windowFrom", notNullValue()).body("data.maxOverlappingBids", is(3));

        float floorFar = as(carrier).get(reqPath(far)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floorFar) + 50)).post(reqPath(far) + "/bids")
                .then().statusCode(201).body("data.warnings", hasSize(0));
        float floorNear = as(carrier).get(reqPath(near)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floorNear) + 50)).post(reqPath(near) + "/bids")
                .then().statusCode(409).body("error.code", is("LOGYX-CAP-005")).body("error.field", is("vehicleId"));
    }

    @Test
    void elMantenimientoBloqueaYVolverADisponibleLoLibera() {
        Session carrier = register("CARRIER", "Mant Carrier");
        String v = vehicle(carrier, plate(), 8000);
        Session pyme = register("PYME", "Mant Pyme");
        String r = publishRoute(pyme, "LIMA", "JULIACA", 1000, 20).statusCode(201).extract().path("data.id");
        as(carrier).body(Map.of("status", "MAINTENANCE")).patch("/api/v1/vehicles/" + v + "/status").then().statusCode(200);
        as(carrier).get(eligPath(r)).then().statusCode(200)
                .body("data.vehicles[0].eligible", is(false)).body("data.vehicles[0].blockerCode", is("LOGYX-CAP-002"));
        float floor = as(carrier).get(reqPath(r)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 10)).post(reqPath(r) + "/bids")
                .then().statusCode(409).body("error.code", is("LOGYX-CAP-002"));
        as(carrier).body(Map.of("status", "AVAILABLE")).patch("/api/v1/vehicles/" + v + "/status").then().statusCode(200);
        as(carrier).get(eligPath(r)).then().body("data.vehicles[0].eligible", is(true));
        bid(carrier, r, v, 10);
    }

    @Test
    void ofertasPendientesSolapadasAvisanYTienenTopeYAlAceptarLaValidacionEsDura() {
        Session carrier = register("CARRIER", "Sobreventa Carrier");
        String v = vehicle(carrier, plate(), 8000);
        Session p1 = register("PYME", "Sobreventa P1");
        Session p2 = register("PYME", "Sobreventa P2");
        String r1 = publishRoute(p1, "LIMA", "JULIACA", 1000, 10).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(p2, "LIMA", "JULIACA", 1000, 11).statusCode(201).extract().path("data.id");
        String r3 = publishRoute(p2, "LIMA", "JULIACA", 1000, 12).statusCode(201).extract().path("data.id");
        String r4 = publishRoute(p2, "LIMA", "JULIACA", 1000, 13).statusCode(201).extract().path("data.id");
        String far = publishRoute(p2, "LIMA", "JULIACA", 1000, 40).statusCode(201).extract().path("data.id");

        String b1 = placeWithWarnings(carrier, v, r1, 0);
        String b2 = placeWithWarnings(carrier, v, r2, 1);
        placeWithWarnings(carrier, v, r3, 2);

        // Antes de ofertar: el aviso de la 4.ª carga ya es un impedimento (tope 3); una lejana está libre
        as(carrier).get(eligPath(r4)).then().statusCode(200)
                .body("data.vehicles[0].eligible", is(false)).body("data.vehicles[0].blockerCode", is("LOGYX-CAP-007"))
                .body("data.vehicles[0].busy", hasSize(3)).body("data.vehicles[0].busy[0].kind", is("BID"));
        as(carrier).get(eligPath(far)).then().body("data.vehicles[0].eligible", is(true))
                .body("data.vehicles[0].warnings", hasSize(0));
        float floor = as(carrier).get(reqPath(r4)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 10)).post(reqPath(r4) + "/bids")
                .then().statusCode(409).body("error.code", is("LOGYX-CAP-007")).body("error.field", is("vehicleId"));

        // Al aceptar la primera, la validación dura: las demás ya no pasan (CAP-005) aunque estén PENDING
        as(p1).post("/api/v1/bids/" + b1 + "/accept").then().statusCode(200);
        as(p2).post("/api/v1/bids/" + b2 + "/accept").then().statusCode(409).body("error.code", is("LOGYX-CAP-005"));
    }

    private String placeWithWarnings(Session carrier, String vehicleId, String requestId, int expectedOverlaps) {
        float floor = as(carrier).get(reqPath(requestId)).then().extract().path("data.priceFloor");
        var r = as(carrier).body(Map.of("vehicleId", vehicleId, "amount", Math.ceil(floor) + 20))
                .post(reqPath(requestId) + "/bids").then().statusCode(201).extract();
        if (expectedOverlaps == 0) {
            assertTrue(r.<java.util.List<Object>>path("data.warnings").isEmpty());
        } else {
            assertEquals("LOGYX-CAP-006", r.path("data.warnings[0].code"));
            String msg = r.path("data.warnings[0].message");
            assertTrue(msg.contains(expectedOverlaps + (expectedOverlaps == 1 ? " oferta" : " ofertas")), msg);
        }
        return r.path("data.id");
    }

    @Test
    void laElegibilidadNoMuestraVehiculosNiOfertasAjenas() {
        Session a = register("CARRIER", "Ajena Alfa");
        Session b = register("CARRIER", "Ajena Beta");
        String va = vehicle(a, plate(), 8000);
        vehicle(b, plate(), 8000);
        Session pyme = register("PYME", "Ajena Pyme");
        String r = publishRoute(pyme, "LIMA", "JULIACA", 1000, 15).statusCode(201).extract().path("data.id");
        float floor = as(a).get(reqPath(r)).then().extract().path("data.priceFloor");
        int amount = (int) Math.ceil(floor) + 777;
        as(a).body(Map.of("vehicleId", va, "amount", amount)).post(reqPath(r) + "/bids").then().statusCode(201);

        String body = as(b).get(eligPath(r)).then().statusCode(200).body("data.vehicles", hasSize(1)).extract().asString();
        assertFalse(body.contains(va), "la elegibilidad de Beta nombra el vehículo de Alfa");
        assertFalse(body.contains(String.valueOf(amount)), "la elegibilidad de Beta revela un monto ajeno");
        as(pyme).get(eligPath(r)).then().statusCode(403);                       // solo transportistas
    }
}
