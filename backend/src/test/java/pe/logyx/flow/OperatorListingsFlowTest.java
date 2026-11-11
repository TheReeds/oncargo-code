package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Listados globales paginados del operador: solicitudes y envíos (lectura por RLS *_operator, sin SystemTx). */
@QuarkusTest
class OperatorListingsFlowTest {

    @Inject AuthService auth;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Listados " + System.nanoTime() % 1000, Api.newRuc(), "Operador Test",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    @Test
    void solicitudesGlobalesConFiltrosYPaginacion() {
        Session op = operator();
        Session pyme = register("PYME", "Lista Pyme A");
        Session pymeB = register("PYME", "Lista Pyme B");
        String a = publishRoute(pyme, "LIMA", "JULIACA", 1000, 2).statusCode(201).extract().path("data.id");
        String b = publishRoute(pymeB, "AREQUIPA", "LIMA", 1500, 3).statusCode(201).extract().path("data.id");
        String today = LocalDate.now(ZoneId.of("America/Lima")).toString();

        // Ve solicitudes de varias organizaciones, más recientes primero, con meta de paginación
        as(op).get("/api/v1/operator/requests?size=100").then().statusCode(200)
                .body("data.id", hasItems(a, b)).body("meta.page", is(0)).body("meta.size", is(100))
                .body("meta.total", greaterThanOrEqualTo(2));
        as(op).get("/api/v1/operator/requests?size=1").then().statusCode(200)
                .body("data", hasSize(1)).body("meta.total", greaterThanOrEqualTo(2));
        as(op).get("/api/v1/operator/requests?size=1&page=1").then().statusCode(200).body("data", hasSize(1));

        // Filtros: ciudad, estado, fechas
        as(op).get("/api/v1/operator/requests?originCity=AREQUIPA&destinationCity=LIMA&size=100").then().statusCode(200)
                .body("data.id", hasItem(b)).body("data.id", not(hasItem(a)))
                .body("data.originCity", everyItem(is("AREQUIPA")));
        as(op).get("/api/v1/operator/requests?status=CANCELLED&size=100").then().statusCode(200)
                .body("data.id", not(hasItems(a, b)));
        as(op).get("/api/v1/operator/requests?status=published&fromDate=" + today + "&toDate=" + today + "&size=100")
                .then().statusCode(200).body("data.id", hasItems(a, b));
        as(op).get("/api/v1/operator/requests?toDate=2000-01-01&size=100").then().statusCode(200)
                .body("data", hasSize(0)).body("meta.total", is(0));

        // Validación
        as(op).get("/api/v1/operator/requests?status=NOPE").then().statusCode(400).body("error.field", is("status"));
        as(op).get("/api/v1/operator/requests?originCity=BOGOTA").then().statusCode(400)
                .body("error.code", is("LOGYX-PRC-001"));
        as(op).get("/api/v1/operator/requests?fromDate=ayer").then().statusCode(400).body("error.field", is("fromDate"));
        as(op).get("/api/v1/operator/requests?fromDate=2030-01-02&toDate=2030-01-01").then().statusCode(400);
        as(op).get("/api/v1/operator/requests?size=500").then().statusCode(400).body("error.field", is("size"));
    }

    @Test
    void enviosGlobalesConFiltrosYPaginacion() {
        Session op = operator();
        Flows.Awarded x = Flows.awarded("Lista Envio");   // LIMA -> JULIACA, PENDING
        String today = LocalDate.now(ZoneId.of("America/Lima")).toString();

        as(op).get("/api/v1/operator/shipments?size=100").then().statusCode(200)
                .body("data.id", hasItem(x.shipmentId())).body("meta.page", is(0)).body("meta.total", greaterThanOrEqualTo(1))
                .body("data.find { it.id == '" + x.shipmentId() + "' }.originCity", is("LIMA"));
        as(op).get("/api/v1/operator/shipments?size=1").then().statusCode(200).body("data", hasSize(1));
        as(op).get("/api/v1/operator/shipments?status=PENDING&originCity=LIMA&destinationCity=JULIACA"
                        + "&fromDate=" + today + "&toDate=" + today + "&size=100").then().statusCode(200)
                .body("data.id", hasItem(x.shipmentId())).body("data.status", everyItem(is("PENDING")));
        as(op).get("/api/v1/operator/shipments?status=DELIVERED&size=100").then().statusCode(200)
                .body("data.id", not(hasItem(x.shipmentId())));
        as(op).get("/api/v1/operator/shipments?originCity=AREQUIPA&size=100").then().statusCode(200)
                .body("data.id", not(hasItem(x.shipmentId())));
        as(op).get("/api/v1/operator/shipments?toDate=2000-01-01").then().statusCode(200).body("meta.total", is(0));
        as(op).get("/api/v1/operator/shipments?status=NOPE").then().statusCode(400).body("error.field", is("status"));
        as(op).get("/api/v1/operator/shipments?page=-1").then().statusCode(400).body("error.field", is("page"));
    }

    @Test
    void pymeYTransportistaNoAccedenALosListadosGlobales() {
        Session pyme = register("PYME", "Lista Pyme 403");
        Session carrier = register("CARRIER", "Lista Carrier 403");
        for (String path : new String[]{"/api/v1/operator/requests", "/api/v1/operator/shipments"}) {
            as(pyme).get(path).then().statusCode(403);
            as(carrier).get(path).then().statusCode(403);
            io.restassured.RestAssured.given().get(path).then().statusCode(401);
        }
        Session driver = Flows.awarded("Lista Conductor").driver();
        as(driver).get("/api/v1/operator/shipments").then().statusCode(403);
    }

    @Test
    void misViajesSeOrdenaYPagina() {
        Session carrier = register("CARRIER", "Orden Viajes");
        String v = vehicle(carrier, plate(), 8000);
        String late = publishTrip(carrier, v, 5000, "PER_KG", 0.4, 9).extract().path("data.id");
        String early = publishTrip(carrier, v, 5000, "PER_KG", 0.4, 3).extract().path("data.id");

        // Sin parámetros: compatible (todos, salida más próxima primero)
        as(carrier).get("/api/v1/trips/mine").then().statusCode(200).body("data.id", contains(early, late))
                .body("meta.page", nullValue());
        as(carrier).get("/api/v1/trips/mine?sort=departure_desc").then().statusCode(200)
                .body("data.id", contains(late, early));
        as(carrier).get("/api/v1/trips/mine?sort=recent").then().statusCode(200).body("data.id", contains(early, late));
        as(carrier).get("/api/v1/trips/mine?page=0&size=1&sort=departure_desc").then().statusCode(200)
                .body("data.id", contains(late)).body("meta.total", is(2)).body("meta.size", is(1));
        as(carrier).get("/api/v1/trips/mine?page=1&size=1&sort=departure_desc").then().body("data.id", contains(early));
        as(carrier).get("/api/v1/trips/mine?sort=zzz").then().statusCode(400).body("error.field", is("sort"));
    }
}
