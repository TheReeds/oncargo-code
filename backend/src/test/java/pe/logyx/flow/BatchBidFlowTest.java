package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Roadmap 2.10.3: ofertar a varias cargas de una vez, por ítem y sin atomicidad. */
@QuarkusTest
class BatchBidFlowTest {

    @Test
    void cadaOfertaSeEvaluaPorSeparado() {
        Session carrier = register("CARRIER", "Lote Carrier");
        String v = vehicle(carrier, plate(), 8000);
        Session pyme = register("PYME", "Lote Pyme");
        String r1 = publishRoute(pyme, "LIMA", "AREQUIPA", 1000, 25).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(pyme, "LIMA", "AREQUIPA", 1000, 40).statusCode(201).extract().path("data.id");
        String r3 = publishRoute(pyme, "LIMA", "AREQUIPA", 1000, 50).statusCode(201).extract().path("data.id");
        float f1 = as(carrier).get("/api/v1/shipment-requests/" + r1).then().extract().path("data.priceFloor");
        float f2 = as(carrier).get("/api/v1/shipment-requests/" + r2).then().extract().path("data.priceFloor");

        as(carrier).body(Map.of("items", List.of(
                        Map.of("requestId", r1, "vehicleId", v, "amount", Math.ceil(f1) + 50),
                        Map.of("requestId", r2, "vehicleId", v, "amount", Math.floor(f2) - 100),       // bajo el piso (RN03)
                        Map.of("requestId", r3, "vehicleId", v, "amount", 99999))))                      // ok
                .post("/api/v1/bids/batch").then().statusCode(200)
                .body("data", hasSize(3))
                .body("data[0].ok", is(true)).body("data[0].bidId", notNullValue())
                .body("data[1].ok", is(false)).body("data[1].code", is("LOGYX-BID-001")).body("data[1].field", is("amount"))
                .body("data[2].ok", is(true));
        as(carrier).get("/api/v1/shipment-requests/my-bids").then().body("data", hasSize(2));
        // Repetir el primero: BID-003 solo en ese ítem
        as(carrier).body(Map.of("items", List.of(Map.of("requestId", r1, "vehicleId", v, "amount", Math.ceil(f1) + 60))))
                .post("/api/v1/bids/batch").then().statusCode(200).body("data[0].code", is("LOGYX-BID-003"));
        as(carrier).body(Map.of("items", List.of())).post("/api/v1/bids/batch").then().statusCode(400);
        as(pyme).body(Map.of("items", List.of(Map.of("requestId", r1, "vehicleId", v, "amount", 5000))))
                .post("/api/v1/bids/batch").then().statusCode(403);
    }
}
