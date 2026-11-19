package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Roadmap 2.13: agenda de flota (por vehículo y conductor), asignación sugerida y asignar vehículo + conductor de una vez. */
@QuarkusTest
class FleetPlanningFlowTest {

    private static String schedule(int fromPlus, int days) {
        return "/api/v1/fleet/schedule?from=" + LocalDate.now().plusDays(fromPlus) + "&to="
                + LocalDate.now().plusDays(fromPlus + days - 1);
    }

    @Test
    void elCalendarioMuestraEnviosOfertasYViajesPorVehiculoYConductor() {
        var a = Flows.awarded("Agenda13");
        Session carrier = a.carrier();
        // Una oferta pendiente con otro vehículo y un viaje publicado
        String v2 = vehicle(carrier, plate(), 8000);
        Session pyme2 = register("PYME", "Agenda13 Pyme2");
        String r2 = publishRoute(pyme2, "LIMA", "ICA", 800, 4).statusCode(201).extract().path("data.id");
        bid(carrier, r2, v2, 40);

        var s = as(carrier).get(schedule(0, 7)).then().statusCode(200)
                .body("data.vehicles", hasSize(2)).body("data.drivers", hasSize(1));
        s.body("data.vehicles.find { it.slots.kind.contains('SHIPMENT') }.slots[0].label", containsString("Lima"))
                .body("data.vehicles.find { it.id == '" + v2 + "' }.slots[0].kind", is("BID"))
                .body("data.drivers[0].slots[0].kind", is("SHIPMENT"))
                .body("data.drivers[0].slots[0].refId", is(a.shipmentId()));

        // Fuera del rango no hay compromisos; el rango máximo es 14 días
        as(carrier).get(schedule(60, 7)).then().statusCode(200)
                .body("data.vehicles.slots.flatten()", hasSize(0)).body("data.drivers.slots.flatten()", hasSize(0));
        as(carrier).get(schedule(0, 15)).then().statusCode(400).body("error.code", is("LOGYX-FLT-011"));
        as(carrier).get("/api/v1/fleet/schedule").then().statusCode(400);
        as(carrier).get(schedule(0, 14)).then().statusCode(200);
        // Lo de un transportista no aparece en otro; ni la PYME la lee
        as(register("CARRIER", "Agenda13 Ajeno")).get(schedule(0, 7)).then().statusCode(200)
                .body("data.vehicles", hasSize(0)).body("data.drivers", hasSize(0));
        as(a.pyme()).get(schedule(0, 7)).then().statusCode(403);
    }

    @Test
    void sugiereConductoresLibresYAsignaVehiculoYConductorEnUnPaso() {
        var a = Flows.awarded("Sugiere13");
        Session carrier = a.carrier();
        String[] libre = driver(carrier, "Ana Libre");
        String vOtro = vehicle(carrier, plate(), 8000);
        String vChico = vehicleOf(carrier, "VAN", 300, null);

        var sug = as(carrier).get("/api/v1/fleet/assignment-suggestions?shipmentId=" + a.shipmentId()).then().statusCode(200)
                .body("data.shipment.assignable", is(true)).body("data.drivers", hasSize(2))
                .body("data.drivers.find { it.current }.assignable", is(true))
                .body("data.drivers.find { it.name == 'Ana Libre' }.free", is(true))
                .body("data.vehicles.find { it.vehicleId == '" + vOtro + "' }.eligible", is(true))
                .body("data.vehicles.find { it.vehicleId == '" + vChico + "' }.eligible", is(false))
                .body("data.vehicles.find { it.vehicleId == '" + vChico + "' }.blockerCode", is("LOGYX-CAP-001"));
        sug.body("data.vehicles.find { it.current }.eligible", is(true));
        as(a.pyme()).get("/api/v1/fleet/assignment-suggestions?shipmentId=" + a.shipmentId()).then().statusCode(403);
        as(register("CARRIER", "Sugiere13 Ajeno")).get("/api/v1/fleet/assignment-suggestions?shipmentId=" + a.shipmentId())
                .then().statusCode(404);

        // Vehículo no apto: falla y no cambia nada
        as(carrier).body(Map.of("driverId", libre[0], "vehicleId", vChico)).put("/api/v1/shipments/" + a.shipmentId() + "/assignment")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-001"));
        // Conductor y vehículo a la vez
        as(carrier).body(Map.of("driverId", libre[0], "vehicleId", vOtro)).put("/api/v1/shipments/" + a.shipmentId() + "/assignment")
                .then().statusCode(200).body("data.vehiclePlate", notNullValue()).body("data.driverName", is("Ana Libre"));
        as(carrier).get("/api/v1/shipments/" + a.shipmentId()).then().body("data.driverName", is("Ana Libre"));
        as(carrier).body(Map.of()).put("/api/v1/shipments/" + a.shipmentId() + "/assignment").then().statusCode(400);
        as(a.pyme()).body(Map.of("driverId", libre[0])).put("/api/v1/shipments/" + a.shipmentId() + "/assignment").then().statusCode(403);
    }

    @Test
    void laAsignacionEsAtomicaSiElConductorEstaOcupado() {
        Session carrier = register("CARRIER", "Atomica13");
        String v1 = vehicle(carrier, plate(), 8000), v2 = vehicle(carrier, plate(), 8000), v3 = vehicle(carrier, plate(), 8000);
        String[] d = driver(carrier, "Conductor Ocupado");
        Session pymeA = register("PYME", "Atomica13 A"), pymeB = register("PYME", "Atomica13 B");
        String ra = publishRoute(pymeA, "LIMA", "JULIACA", 1000, 8).statusCode(201).extract().path("data.id");
        String rb = publishRoute(pymeB, "LIMA", "JULIACA", 1000, 8).statusCode(201).extract().path("data.id");
        String sa = as(pymeA).post("/api/v1/bids/" + bid(carrier, ra, v1, 50) + "/accept").then().statusCode(200).extract().path("data.shipmentId");
        String sb = as(pymeB).post("/api/v1/bids/" + bid(carrier, rb, v2, 50) + "/accept").then().statusCode(200).extract().path("data.shipmentId");
        as(carrier).body(Map.of("driverId", d[0])).put("/api/v1/shipments/" + sa + "/driver").then().statusCode(200);

        var sug = as(carrier).get("/api/v1/fleet/assignment-suggestions?shipmentId=" + sb).then().statusCode(200)
                .body("data.drivers.find { it.name == 'Conductor Ocupado' }.free", is(false))
                .body("data.drivers.find { it.name == 'Conductor Ocupado' }.assignable", is(false));
        // v3 + conductor ocupado: 409 y el vehículo del envío B sigue siendo v2 (nada a medias)
        String plateV2 = as(carrier).get("/api/v1/shipments/" + sb).then().extract().path("data.vehiclePlate");
        as(carrier).body(Map.of("driverId", d[0], "vehicleId", v3)).put("/api/v1/shipments/" + sb + "/assignment")
                .then().statusCode(409).body("error.code", is("LOGYX-SHP-004"));
        as(carrier).get("/api/v1/shipments/" + sb).then().body("data.vehiclePlate", is(plateV2));
    }
}
