package pe.logyx.support;

import pe.logyx.support.Api.Session;

import java.util.List;
import java.util.Map;

import static pe.logyx.support.Api.*;

/** Escenarios armados por la API para los tests de las fases 2+ (negociación, evidencia, incidencias...). */
public final class Flows {

    private Flows() {}

    /** Solicitud publicada con una oferta pendiente. */
    public record Bidding(Session pyme, Session carrier, String requestId, String bidId, String vehicleId,
                          float floor, float bidAmount) {
        /** Monto entero por encima del piso (comparable exacto como float en las aserciones). */
        public float above(int delta) { return (float) Math.ceil(floor) + delta; }
    }

    /** Envío adjudicado con conductor asignado (PENDING). */
    public record Awarded(Session pyme, Session carrier, Session driver, String requestId, String bidId,
                          String shipmentId, String pickupStopId, String deliveryStopId) {

        public String stop(String stopId, String action) {
            return "/api/v1/shipments/" + shipmentId + "/stops/" + stopId + "/" + action;
        }
    }

    public static Bidding bidding(String tag) {
        Session pyme = register("PYME", "Pyme " + tag);
        Session carrier = register("CARRIER", "Carrier " + tag);
        String vehicle = vehicle(carrier, plate(), 8000);
        var req = publish(pyme, 2000).statusCode(201).extract();
        String requestId = req.path("data.id");
        float floor = req.path("data.priceFloor");
        float amount = (float) Math.ceil(floor) + 300;
        String bidId = Api.as(carrier).body(Map.of("vehicleId", vehicle, "amount", amount))
                .post("/api/v1/shipment-requests/" + requestId + "/bids").then().statusCode(201)
                .extract().path("data.id");
        return new Bidding(pyme, carrier, requestId, bidId, vehicle, floor, amount);
    }

    public static Awarded awarded(String tag) {
        Bidding b = bidding(tag);
        String driverEmail = "chofer" + System.nanoTime() + "@test.pe";
        String driverId = Api.as(b.carrier()).body(Map.of("fullName", "Chofer " + tag, "email", driverEmail,
                        "password", "Chofer1234", "licenseNumber", "Q11223344"))
                .post("/api/v1/drivers").then().statusCode(201).extract().path("data.id");
        String shipmentId = Api.as(b.pyme()).post("/api/v1/bids/" + b.bidId() + "/accept").then().statusCode(200)
                .extract().path("data.shipmentId");
        Api.as(b.carrier()).body(Map.of("driverId", driverId)).put("/api/v1/shipments/" + shipmentId + "/driver")
                .then().statusCode(200);
        Session driver = login(driverEmail, "Chofer1234");
        List<String> stops = Api.as(driver).get("/api/v1/shipments/" + shipmentId).then().statusCode(200)
                .extract().path("data.stops.id");
        return new Awarded(b.pyme(), b.carrier(), driver, b.requestId(), b.bidId(), shipmentId, stops.get(0),
                stops.get(1));
    }

    /** Recojo completado: envío IN_TRANSIT. */
    public static Awarded inTransit(String tag) {
        Awarded a = awarded(tag);
        Api.as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        Api.as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200);
        return a;
    }

    /** Entregado con foto de evidencia. */
    public static Awarded delivered(String tag) {
        Awarded a = inTransit(tag);
        Api.as(a.driver()).post(a.stop(a.deliveryStopId(), "arrive")).then().statusCode(200);
        deliveryPhoto(a.driver(), a.shipmentId(), a.deliveryStopId());
        Api.as(a.driver()).post(a.stop(a.deliveryStopId(), "complete")).then().statusCode(200);
        return a;
    }
}
