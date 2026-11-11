package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.trip.application.SmartLoadService;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Smart Load Planner (OE07): generación bajo demanda y programada, aceptar/rechazar y avisos. */
@QuarkusTest
class SmartLoadFlowTest {

    @Inject EntityManager em;
    @Inject pe.logyx.shared.tenancy.SystemTx systemTx;
    @Inject SmartLoadService service;

    /**
     * El planificador mira todas las solicitudes publicadas, incluidas las que dejan otros tests en la misma base. Para que los
     * resultados dependan solo de lo que arma cada test, se cancelan las publicadas de los próximos 30 días (estos tests usan
     * fechas a +40 días o más).
     */
    @BeforeEach
    void aislarPublicadas() {
        systemTx.run(() -> em.createNativeQuery(
                "UPDATE shipment_requests SET status = 'CANCELLED' WHERE status = 'PUBLISHED' "
                        + "AND pickup_date < current_date + 30").executeUpdate());
    }

    @Test
    void generaSugerenciaConCargasCompatiblesYElTransportistaLaAcepta() {
        Session carrier = register("CARRIER", "Smart Uno");
        String vehicle = vehicle(carrier, plate(), 8000);
        Session pyme1 = register("PYME", "Smart Pyme Uno");
        Session pyme2 = register("PYME", "Smart Pyme Dos");
        // Fecha aislada (+40 días) para que otros tests no se mezclen en la combinación
        String r1 = publishRoute(pyme1, "LIMA", "JULIACA", 3000, 40).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(pyme2, "LIMA", "PUNO", 2500, 40).statusCode(201).extract().path("data.id");
        publishRoute(pyme2, "LIMA", "TACNA", 2500, 40).statusCode(201);          // destino lejano: no entra

        var created = as(carrier).post("/api/v1/smart-loads/generate").then().statusCode(201)
                .body("data", hasSize(1))
                .body("data[0].status", is("PENDING")).body("data[0].vehicleId", is(vehicle))
                .body("data[0].items", hasSize(2)).body("data[0].items.requestId", hasItems(r1, r2))
                .body("data[0].totalWeightKg", num(5500)).body("data[0].occupancyPct", num(68.75))
                .body("data[0].combinedIncome", not(num(0)))
                .body("data[0].route", hasSize(4)).body("data[0].route[0].type", is("PICKUP"))
                .body("data[0].route[3].type", is("DELIVERY")).body("data[0].expiresAt", notNullValue())
                .extract();
        String id = created.path("data[0].id");

        // Ya hay una pendiente para ese vehículo: no se duplica
        as(carrier).post("/api/v1/smart-loads/generate").then().statusCode(201).body("data", hasSize(0));
        as(carrier).get("/api/v1/smart-loads?status=PENDING").then().statusCode(200).body("data.id", hasItem(id));
        as(carrier).get("/api/v1/smart-loads?status=NOPE").then().statusCode(400);
        as(carrier).get("/api/v1/notifications").then().body("data.type", hasItem("SMART_LOAD_SUGGESTED"))
                .body("data.find { it.type == 'SMART_LOAD_SUGGESTED' }.relatedType", is("SMART_LOAD"))
                .body("data.find { it.type == 'SMART_LOAD_SUGGESTED' }.relatedId", is(id));

        // Otro transportista ni la ve ni la resuelve; las PYMEs no usan el planificador
        Session other = register("CARRIER", "Smart Otro");
        as(other).get("/api/v1/smart-loads").then().body("data.id", not(hasItem(id)));
        as(other).post("/api/v1/smart-loads/" + id + "/accept").then().statusCode(404);
        as(pyme1).get("/api/v1/smart-loads").then().statusCode(403);

        // Aceptar: marca, no adjudica; avisa a las PYMEs agrupadas
        as(carrier).post("/api/v1/smart-loads/" + id + "/accept").then().statusCode(200)
                .body("data.status", is("ACCEPTED")).body("data.decidedAt", notNullValue());
        as(carrier).post("/api/v1/smart-loads/" + id + "/accept").then().statusCode(409).body("error.code", is("LOGYX-TRP-008"));
        as(carrier).post("/api/v1/smart-loads/" + id + "/reject").then().statusCode(409).body("error.code", is("LOGYX-TRP-008"));
        as(pyme1).get("/api/v1/shipment-requests/" + r1).then().body("data.status", is("PUBLISHED"));
        as(pyme1).get("/api/v1/notifications").then().body("data.type", hasItem("SMART_LOAD_ACCEPTED"))
                .body("data.find { it.type == 'SMART_LOAD_ACCEPTED' }.relatedType", is("REQUEST"))
                .body("data.find { it.type == 'SMART_LOAD_ACCEPTED' }.relatedId", is(r1))
                .body("data.find { it.type == 'SMART_LOAD_ACCEPTED' }.body", containsString("Smart Uno"));
        as(pyme2).get("/api/v1/notifications").then().body("data.type", hasItem("SMART_LOAD_ACCEPTED"));
    }

    @Test
    void rechazarVencerYElJobProgramado() {
        Session carrier = register("CARRIER", "Smart Dos");
        vehicle(carrier, plate(), 5000);
        Session pyme = register("PYME", "Smart Pyme Tres");
        // +50 días, cargas Arequipa → Cusco... sin vehículo ocupado: 2500 + 2000 = 90 % de 5000
        publishRoute(pyme, "LIMA", "AREQUIPA", 2500, 50).statusCode(201);
        publishRoute(pyme, "LIMA", "AREQUIPA", 2000, 51).statusCode(201);

        // El job programado (sin usuario, rol dueño) también genera para todos los transportistas
        int created = service.generateForAllCarriers();
        org.junit.jupiter.api.Assertions.assertTrue(created >= 1);
        String id = as(carrier).get("/api/v1/smart-loads?status=PENDING").then().statusCode(200)
                .body("data", hasSize(1)).body("data[0].items", hasSize(2)).extract().path("data[0].id");

        as(carrier).post("/api/v1/smart-loads/" + id + "/reject").then().statusCode(200).body("data.status", is("REJECTED"));
        as(carrier).get("/api/v1/smart-loads?status=REJECTED").then().body("data.id", hasItem(id));
        as(carrier).get("/api/v1/smart-loads?status=PENDING").then().body("data", hasSize(0));

        // Tras rechazar, el planificador puede volver a proponerla; esa nueva vence
        String again = as(carrier).post("/api/v1/smart-loads/generate").then().statusCode(201).body("data", hasSize(1))
                .extract().path("data[0].id");
        systemTx.run(() -> em.createNativeQuery(
                "UPDATE smart_load_suggestions SET expires_at = now() - interval '1 minute' WHERE id = ?1")
                .setParameter(1, UUID.fromString(again)).executeUpdate());
        as(carrier).post("/api/v1/smart-loads/" + again + "/accept").then().statusCode(409).body("error.code", is("LOGYX-TRP-009"));
        as(carrier).get("/api/v1/dashboard/summary").then().body("data.pendingSmartLoads", is(0));
    }

    @Test
    void sinVehiculoDisponibleOSinCargasNoHaySugerencias() {
        Session carrier = register("CARRIER", "Smart Vacio");
        as(carrier).post("/api/v1/smart-loads/generate").then().statusCode(201).body("data", hasSize(0));
        vehicle(carrier, plate(), 8000);
        as(carrier).post("/api/v1/smart-loads/generate").then().statusCode(201);
        as(carrier).get("/api/v1/smart-loads").then().statusCode(200);
        as(carrier).body(Map.of()).post("/api/v1/smart-loads/" + UUID.randomUUID() + "/accept").then().statusCode(404);
    }
}
