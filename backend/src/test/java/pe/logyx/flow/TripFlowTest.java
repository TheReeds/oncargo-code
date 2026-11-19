package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;
import pe.logyx.trip.application.TripMaintenanceUseCase;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static pe.logyx.support.Api.*;

/** Viajes publicados (RN12), reservas, y su integración con envíos y notificaciones. */
@QuarkusTest
class TripFlowTest {

    @Inject EntityManager em;
    @Inject pe.logyx.shared.tenancy.SystemTx systemTx;
    @Inject TripMaintenanceUseCase maintenance;

    @Test
    void publicarValidaRn12YFechaYLaPymeLoEncuentra() {
        Session carrier = register("CARRIER", "Trip Pub");
        String vehicle = vehicle(carrier, plate(), 8000);

        // RN12: más que el vehículo
        publishTrip(carrier, vehicle, 9000, "PER_KG", 0.5, 3).statusCode(400)
                .body("error.code", is("LOGYX-TRP-001")).body("error.field", is("capacityKg"));
        // salida pasada
        publishTrip(carrier, vehicle, 5000, "PER_KG", 0.5, -1).statusCode(400)
                .body("error.code", is("LOGYX-VAL-001")).body("error.field", is("departureAt"));
        // vehículo ajeno
        Session other = register("CARRIER", "Trip Otro");
        publishTrip(other, vehicle, 5000, "PER_KG", 0.5, 3).statusCode(404);
        // ciudades fuera del corredor / iguales
        as(carrier).body(Map.of("originCity", "BOGOTA", "destinationCity", "JULIACA", "departureAt", departure(3),
                        "vehicleId", vehicle, "capacityKg", 5000, "pricingMode", "PER_KG", "rate", 0.5))
                .post("/api/v1/trips").then().statusCode(400).body("error.code", is("LOGYX-PRC-001"));
        as(carrier).body(Map.of("originCity", "LIMA", "destinationCity", "LIMA", "departureAt", departure(3),
                        "vehicleId", vehicle, "capacityKg", 5000, "pricingMode", "PER_KG", "rate", 0.5))
                .post("/api/v1/trips").then().statusCode(400).body("error.code", is("LOGYX-PRC-002"));
        // roles: la PYME no publica
        Session pyme = register("PYME", "Trip Pyme");
        as(pyme).body(Map.of()).post("/api/v1/trips").then().statusCode(403);

        String tripId = publishTrip(carrier, vehicle, 8000, "PER_KG", 0.5, 3).statusCode(201)
                .body("data.status", is("OPEN")).body("data.capacityKg", num(8000)).body("data.remainingKg", num(8000))
                .body("data.confirmedWeightKg", num(0)).body("data.vehicleId", is(vehicle))
                .body("data.pendingBookings", is(0)).body("data.carrierName", is("Trip Pub"))
                .body("data.originCity", is("LIMA")).extract().path("data.id");

        as(carrier).get("/api/v1/trips/mine").then().statusCode(200).body("data.id", hasItem(tripId));
        as(carrier).get("/api/v1/trips/mine?page=0&size=5").then().body("meta.total", greaterThanOrEqualTo(1));

        // La PYME lo ve con filtros, sin el vehículo privado
        as(pyme).get("/api/v1/trips?originCity=LIMA&destinationCity=JULIACA&minWeightKg=7000&size=100").then()
                .statusCode(200).body("data.id", hasItem(tripId)).body("meta.total", greaterThanOrEqualTo(1));
        as(pyme).get("/api/v1/trips?minWeightKg=8001&originCity=LIMA&destinationCity=JULIACA&size=100").then()
                .body("data.id", not(hasItem(tripId)));
        as(pyme).get("/api/v1/trips?originCity=CUSCO&size=100").then().body("data.id", not(hasItem(tripId)));
        as(pyme).get("/api/v1/trips?fromDate=2000-01-01&toDate=2000-01-02&size=100").then()
                .body("data.id", not(hasItem(tripId)));
        as(pyme).get("/api/v1/trips?fromDate=hoy").then().statusCode(400).body("error.field", is("fromDate"));
        as(pyme).get("/api/v1/trips?originCity=XX").then().statusCode(400).body("error.code", is("LOGYX-PRC-001"));
        as(pyme).get("/api/v1/trips/" + tripId).then().statusCode(200).body("data.vehicleId", nullValue())
                .body("data.vehiclePlate", notNullValue()).body("data.pendingBookings", nullValue());
        // otro transportista no ve el viaje
        as(other).get("/api/v1/trips/" + tripId).then().statusCode(404);
        as(other).post("/api/v1/trips/" + tripId + "/cancel").then().statusCode(404);
        as(other).get("/api/v1/trips/" + tripId + "/bookings").then().statusCode(404);
    }

    @Test
    void reservarConfirmarDescuentaCapacidadGeneraEnvioYCierraAlLlenarse() {
        Session carrier = register("CARRIER", "Trip Book");
        Session p1 = register("PYME", "Trip Book Uno");
        Session p2 = register("PYME", "Trip Book Dos");
        Session p3 = register("PYME", "Trip Book Tres");
        String tripId = publishTrip(carrier, vehicle(carrier, plate(), 8000), 8000, "PER_KG", 0.5, 3).statusCode(201)
                .extract().path("data.id");

        // RF23: más de lo que cabe
        bookTrip(p1, tripId, 8001).statusCode(409).body("error.code", is("LOGYX-TRP-004"))
                .body("error.field", is("weightKg"));
        // validación de cuerpo
        as(p1).body(Map.of("weightKg", 0, "cargoDescription", "x", "cargoType", "GENERAL",
                "pickupAddress", "a", "deliveryAddress", "b")).post("/api/v1/trips/" + tripId + "/bookings").then()
                .statusCode(400).body("error.code", is("LOGYX-VAL-001"));
        // el transportista no reserva
        bookTrip(carrier, tripId, 100).statusCode(403);

        String b1 = bookTrip(p1, tripId, 3000).statusCode(201).body("data.status", is("PENDING"))
                .body("data.price", num(1500.0)).body("data.shipmentId", nullValue())
                .body("data.requesterName", is("Trip Book Uno")).extract().path("data.id");
        String b2 = bookTrip(p2, tripId, 5000).statusCode(201).extract().path("data.id");
        String b3 = bookTrip(p3, tripId, 1000).statusCode(201).extract().path("data.id");

        // Pendientes no descuentan; el transportista las ve con sus conteos
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.pendingBookings", is(3))
                .body("data.remainingKg", num(8000)).body("data.status", is("OPEN"));
        as(carrier).get("/api/v1/trips/" + tripId + "/bookings").then().statusCode(200).body("data", hasSize(3))
                .body("data.status", everyItem(is("PENDING")));
        // Cada PYME solo ve la suya
        as(p1).get("/api/v1/trip-bookings/mine").then().body("data", hasSize(1)).body("data[0].id", is(b1));
        as(p1).get("/api/v1/trip-bookings/mine?status=CONFIRMED").then().body("data", hasSize(0));
        as(p1).get("/api/v1/trip-bookings/mine?status=NOPE").then().statusCode(400);
        // Aviso al transportista
        as(carrier).get("/api/v1/notifications").then().body("data.type", hasItem("TRIP_BOOKING_REQUESTED"))
                .body("data.find { it.type == 'TRIP_BOOKING_REQUESTED' }.relatedType", is("TRIP"))
                .body("data.find { it.type == 'TRIP_BOOKING_REQUESTED' }.relatedId", is(tripId));

        // Otras organizaciones no pueden resolver
        as(p1).post("/api/v1/trip-bookings/" + b1 + "/confirm").then().statusCode(403);
        as(register("CARRIER", "Trip Intruso")).post("/api/v1/trip-bookings/" + b1 + "/confirm").then().statusCode(404);
        as(p2).post("/api/v1/trip-bookings/" + b1 + "/cancel").then().statusCode(404);

        // Confirmar la primera: FILLING, envío generado y visible para ambas partes
        String shipmentId = as(carrier).post("/api/v1/trip-bookings/" + b1 + "/confirm").then().statusCode(200)
                .body("data.status", is("CONFIRMED")).body("data.shipmentId", notNullValue())
                .body("data.requestId", notNullValue()).extract().path("data.shipmentId");
        as(carrier).post("/api/v1/trip-bookings/" + b1 + "/confirm").then().statusCode(409)
                .body("error.code", is("LOGYX-TRP-006"));
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("FILLING"))
                .body("data.remainingKg", num(5000)).body("data.confirmedWeightKg", num(3000))
                .body("data.confirmedBookings", is(1));
        as(p1).get("/api/v1/shipments/" + shipmentId).then().statusCode(200).body("data.status", is("PENDING"))
                .body("data.agreedPrice", num(1500.0)).body("data.stops", hasSize(2))
                .body("data.stops[0].type", is("PICKUP")).body("data.stops[0].city", is("LIMA"))
                .body("data.stops[1].city", is("JULIACA")).body("data.stops[0].address", containsString("Callao"))
                .body("data.vehiclePlate", notNullValue());
        as(carrier).get("/api/v1/shipments/" + shipmentId).then().statusCode(200);
        as(p2).get("/api/v1/shipments/" + shipmentId).then().statusCode(404);
        // la PYME ve su solicitud adjudicada y avisos
        as(p1).get("/api/v1/shipment-requests/mine").then().body("data.status", hasItem("MATCHED"));
        as(p1).get("/api/v1/notifications").then().body("data.type", hasItem("TRIP_BOOKING_CONFIRMED"))
                .body("data.find { it.type == 'TRIP_BOOKING_CONFIRMED' }.relatedType", is("SHIPMENT"))
                .body("data.find { it.type == 'TRIP_BOOKING_CONFIRMED' }.relatedId", is(shipmentId));
        // La PYME ve el viaje (OPEN/FILLING) con la capacidad restante
        as(p3).get("/api/v1/trips/" + tripId).then().statusCode(200).body("data.remainingKg", num(5000));

        // Confirmar la segunda llena el viaje → CLOSED y la tercera (pendiente) se rechaza sola
        as(carrier).post("/api/v1/trip-bookings/" + b2 + "/confirm").then().statusCode(200);
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("CLOSED"))
                .body("data.remainingKg", num(0));
        as(p3).get("/api/v1/trip-bookings/mine").then().body("data[0].id", is(b3)).body("data[0].status", is("REJECTED"))
                .body("data[0].decisionNote", notNullValue());
        as(p3).get("/api/v1/notifications").then().body("data.type", hasItem("TRIP_BOOKING_REJECTED"));
        // Cerrado: ya no aparece en la búsqueda ni admite reservas (404 para quien no reservó)
        as(register("PYME", "Trip Book Cuatro")).get("/api/v1/trips?originCity=LIMA&size=100").then()
                .body("data.id", not(hasItem(tripId)));
        bookTrip(register("PYME", "Trip Book Cinco"), tripId, 10).statusCode(404);
        // Quien reservó sigue viéndolo
        as(p1).get("/api/v1/trips/" + tripId).then().statusCode(200).body("data.status", is("CLOSED"));
    }

    @Test
    void rechazarYCancelarLiberanCapacidadYAvisan() {
        Session carrier = register("CARRIER", "Trip Rej");
        Session pyme = register("PYME", "Trip Rej Pyme");
        String tripId = publishTrip(carrier, vehicle(carrier, plate(), 6000), 6000, "PER_TRIP", 3000, 4).statusCode(201)
                .extract().path("data.id");

        // PER_TRIP prorratea: 1500 kg de 6000 → 25 % de 3000 = 750
        String b1 = bookTrip(pyme, tripId, 1500).statusCode(201).body("data.price", num(750.0)).extract().path("data.id");
        as(carrier).body(Map.of("reason", "Sin espacio por mercadería previa")).post("/api/v1/trip-bookings/" + b1 + "/reject")
                .then().statusCode(200).body("data.status", is("REJECTED"))
                .body("data.decisionNote", is("Sin espacio por mercadería previa"));
        as(carrier).post("/api/v1/trip-bookings/" + b1 + "/reject").then().statusCode(409)
                .body("error.code", is("LOGYX-TRP-006"));
        as(pyme).post("/api/v1/trip-bookings/" + b1 + "/cancel").then().statusCode(409)
                .body("error.code", is("LOGYX-TRP-006"));
        as(pyme).get("/api/v1/notifications").then().body("data.type", hasItem("TRIP_BOOKING_REJECTED"));

        // Cancelar pendiente
        String b2 = bookTrip(pyme, tripId, 1000).statusCode(201).extract().path("data.id");
        as(pyme).post("/api/v1/trip-bookings/" + b2 + "/cancel").then().statusCode(200)
                .body("data.status", is("CANCELLED"));
        as(carrier).post("/api/v1/trip-bookings/" + b2 + "/confirm").then().statusCode(409);
        as(carrier).get("/api/v1/notifications").then().body("data.type", hasItem("TRIP_BOOKING_CANCELLED"));

        // Cancelar confirmada: cancela el envío y devuelve la capacidad (FILLING → OPEN)
        String b3 = bookTrip(pyme, tripId, 2000).statusCode(201).extract().path("data.id");
        String shipmentId = as(carrier).post("/api/v1/trip-bookings/" + b3 + "/confirm").then().statusCode(200)
                .extract().path("data.shipmentId");
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("FILLING"))
                .body("data.remainingKg", num(4000));
        as(pyme).post("/api/v1/trip-bookings/" + b3 + "/cancel").then().statusCode(200)
                .body("data.status", is("CANCELLED"));
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("OPEN"))
                .body("data.remainingKg", num(6000)).body("data.confirmedWeightKg", num(0));
        as(pyme).get("/api/v1/shipments/" + shipmentId).then().body("data.status", is("CANCELLED"));
    }

    @Test
    void cerrarYCancelarViaje() {
        Session carrier = register("CARRIER", "Trip Close");
        Session p1 = register("PYME", "Trip Close Uno");
        Session p2 = register("PYME", "Trip Close Dos");
        String vehicle = vehicle(carrier, plate(), 8000);

        // Cierre manual: rechaza pendientes, conserva confirmadas, ya no admite reservas
        String t1 = publishTrip(carrier, vehicle, 8000, "PER_KG", 0.4, 3).extract().path("data.id");
        String bConfirmed = bookTrip(p1, t1, 1000).extract().path("data.id");
        String bPending = bookTrip(p2, t1, 1000).extract().path("data.id");
        as(carrier).post("/api/v1/trip-bookings/" + bConfirmed + "/confirm").then().statusCode(200);
        as(carrier).post("/api/v1/trips/" + t1 + "/close").then().statusCode(200).body("data.status", is("CLOSED"));
        as(p2).get("/api/v1/trip-bookings/mine").then().body("data[0].status", is("REJECTED"));
        as(p1).get("/api/v1/trip-bookings/mine").then().body("data[0].status", is("CONFIRMED"));
        bookTrip(p1, t1, 100).statusCode(409).body("error.code", is("LOGYX-TRP-003"));
        as(carrier).post("/api/v1/trips/" + t1 + "/close").then().statusCode(409).body("error.code", is("LOGYX-TRP-005"));
        as(carrier).post("/api/v1/trip-bookings/" + bPending + "/confirm").then().statusCode(409);

        // Cancelar: cancela reservas (y envíos que no salieron) y avisa a las PYMEs
        String t2 = publishTrip(carrier, vehicle, 8000, "PER_KG", 0.4, 5).extract().path("data.id");
        String c1 = bookTrip(p1, t2, 1000).extract().path("data.id");
        String c2 = bookTrip(p2, t2, 1000).extract().path("data.id");
        String shipmentId = as(carrier).post("/api/v1/trip-bookings/" + c1 + "/confirm").then().statusCode(200)
                .extract().path("data.shipmentId");
        as(carrier).post("/api/v1/trips/" + t2 + "/cancel").then().statusCode(200).body("data.status", is("CANCELLED"));
        as(p1).get("/api/v1/trip-bookings/mine?status=CANCELLED").then().body("data.id", hasItem(c1));
        as(p2).get("/api/v1/trip-bookings/mine?status=CANCELLED").then().body("data.id", hasItem(c2));
        as(p1).get("/api/v1/shipments/" + shipmentId).then().body("data.status", is("CANCELLED"));
        as(p2).get("/api/v1/notifications").then().body("data.type", hasItem("TRIP_CANCELLED"));
        as(carrier).post("/api/v1/trips/" + t2 + "/cancel").then().statusCode(409).body("error.code", is("LOGYX-TRP-005"));
        as(p1).get("/api/v1/trips?originCity=LIMA&size=100").then().body("data.id", not(hasItem(t2)));
    }

    @Test
    void conUnEnvioEnCursoNoSePuedeCancelarElViajeNiLaReserva() {
        Session carrier = register("CARRIER", "Trip Started");
        Session pyme = register("PYME", "Trip Started Pyme");
        String vehicle = vehicle(carrier, plate(), 8000);
        String tripId = publishTrip(carrier, vehicle, 8000, "PER_KG", 0.4, 3).extract().path("data.id");
        String bookingId = bookTrip(pyme, tripId, 2000).extract().path("data.id");
        String shipmentId = as(carrier).post("/api/v1/trip-bookings/" + bookingId + "/confirm").then().statusCode(200)
                .extract().path("data.shipmentId");

        // El flujo normal de seguimiento sigue funcionando sobre el envío generado
        String driverEmail = "chofer" + System.nanoTime() + "@test.pe";
        String driverId = as(carrier).body(Map.of("fullName", "Chofer Viaje", "email", driverEmail,
                        "password", "Chofer1234", "licenseNumber", "Q11223344"))
                .post("/api/v1/drivers").then().statusCode(201).extract().path("data.id");
        as(carrier).body(Map.of("driverId", driverId)).put("/api/v1/shipments/" + shipmentId + "/driver")
                .then().statusCode(200);
        Session driver = login(driverEmail, "Chofer1234");
        java.util.List<String> stops = as(driver).get("/api/v1/shipments/" + shipmentId).then().statusCode(200)
                .extract().path("data.stops.id");
        as(driver).post("/api/v1/shipments/" + shipmentId + "/stops/" + stops.get(0) + "/arrive").then().statusCode(200);
        as(driver).post("/api/v1/shipments/" + shipmentId + "/stops/" + stops.get(0) + "/complete").then().statusCode(200)
                .body("data.status", is("IN_TRANSIT"));

        as(carrier).post("/api/v1/trips/" + tripId + "/cancel").then().statusCode(409)
                .body("error.code", is("LOGYX-TRP-007"));
        as(pyme).post("/api/v1/trip-bookings/" + bookingId + "/cancel").then().statusCode(409)
                .body("error.code", is("LOGYX-SHP-003"));
        // nada cambió
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("FILLING"));
        as(pyme).get("/api/v1/trip-bookings/mine").then().body("data[0].status", is("CONFIRMED"));
    }

    @Test
    void elMantenimientoCierraViajesQueYaSalieronYRechazaPendientes() {
        Session carrier = register("CARRIER", "Trip Maint");
        Session pyme = register("PYME", "Trip Maint Pyme");
        String tripId = publishTrip(carrier, vehicle(carrier, plate(), 8000), 8000, "PER_KG", 0.4, 2).extract().path("data.id");
        String bookingId = bookTrip(pyme, tripId, 500).extract().path("data.id");
        systemTx.run(() -> em.createNativeQuery(
                "UPDATE carrier_trips SET departure_at = now() - interval '2 hours' WHERE id = ?1")
                .setParameter(1, java.util.UUID.fromString(tripId)).executeUpdate());

        maintenance.run();
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("CLOSED"));
        as(pyme).get("/api/v1/trip-bookings/mine").then().body("data.find { it.id == '" + bookingId + "' }.status",
                is("REJECTED"));

        systemTx.run(() -> em.createNativeQuery(
                "UPDATE carrier_trips SET departure_at = now() - interval '30 hours' WHERE id = ?1")
                .setParameter(1, java.util.UUID.fromString(tripId)).executeUpdate());
        maintenance.run();
        as(carrier).get("/api/v1/trips/" + tripId).then().body("data.status", is("COMPLETED"));
    }

    @Test
    void dashboardAgregaCamposDeViajesSinRomperLosExistentes() {
        Session carrier = register("CARRIER", "Trip Dash");
        Session pyme = register("PYME", "Trip Dash Pyme");
        String tripId = publishTrip(carrier, vehicle(carrier, plate(), 8000), 8000, "PER_KG", 0.4, 3).extract().path("data.id");
        bookTrip(pyme, tripId, 500).statusCode(201);

        as(carrier).get("/api/v1/dashboard/summary").then().statusCode(200).body("data.role", is("CARRIER"))
                .body("data.openTrips", is(1)).body("data.pendingTripBookings", is(1))
                .body("data.pendingSmartLoads", is(0)).body("data.returnLoads", is(0))
                .body("data.topReturnLoads", hasSize(0)).body("data.compatibleLoads", notNullValue())
                .body("data.activeTripBookings", nullValue());
        as(pyme).get("/api/v1/dashboard/summary").then().statusCode(200).body("data.role", is("PYME"))
                .body("data.activeTripBookings", is(1)).body("data.activeRequests", is(0))
                .body("data.openTrips", nullValue()).body("data.returnLoads", nullValue());
    }

    @Test
    void cargasDeRetornoParaEnviosEnCursoOEntregados() {
        Flows.Awarded delivered = Flows.delivered("Retorno");      // LIMA → JULIACA entregado, vehículo de 8000 kg
        Session carrier = delivered.carrier();
        Session pyme = register("PYME", "Retorno Pyme");

        // sin solicitudes cercanas: vacío
        as(carrier).get("/api/v1/return-loads").then().statusCode(200).body("data", hasSize(0));

        // Salen de Juliaca (misma ciudad), de Puno (cercana), de Cusco (lejos), una muy pesada y una fuera de ventana
        String juliaca = publishRoute(pyme, "JULIACA", "LIMA", 2000, 1).statusCode(201).extract().path("data.id");
        String puno = publishRoute(pyme, "PUNO", "AREQUIPA", 1500, 2).statusCode(201).extract().path("data.id");
        String cusco = publishRoute(pyme, "CUSCO", "LIMA", 1000, 1).statusCode(201).extract().path("data.id");
        String pesada = publishRoute(pyme, "JULIACA", "LIMA", 12000, 1).statusCode(201).extract().path("data.id");
        String lejana = publishRoute(pyme, "JULIACA", "LIMA", 1000, 20).statusCode(201).extract().path("data.id");
        String haciaJuliaca = publishRoute(pyme, "PUNO", "JULIACA", 1000, 1).statusCode(201).extract().path("data.id");

        var res = as(carrier).get("/api/v1/return-loads").then().statusCode(200)
                .body("data.requestId", hasItems(juliaca, puno))
                .body("data.requestId", not(hasItems(cusco, pesada, lejana, haciaJuliaca)))
                .body("data[0].requestId", is(juliaca))               // misma ciudad primero
                .body("data[0].fromCity", is("JULIACA")).body("data[0].shipmentDelivered", is(true))
                .body("data[0].distanceKm", num(0)).body("data[0].matchScore", is(96))
                .body("data[0].shipmentId", is(delivered.shipmentId())).body("data[0].vehicleId", notNullValue())
                .body("data[0].alreadyBid", is(false)).body("data[0].suggestedPrice", notNullValue())
                .body("data[1].requestId", is(puno)).body("data[1].distanceKm", num(42.9))   // Juliaca → Puno por carretera (ADR-0008)
                .extract();
        String vehicleId = res.path("data[0].vehicleId");

        // RF63: desde la pantalla de retornos se oferta con el vehículo sugerido y queda marcada
        float floor = as(carrier).get("/api/v1/shipment-requests/" + juliaca).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", vehicleId, "amount", (float) Math.ceil(floor) + 100))
                .post("/api/v1/shipment-requests/" + juliaca + "/bids").then().statusCode(201);
        as(carrier).get("/api/v1/return-loads").then().body("data.find { it.requestId == '" + juliaca + "' }.alreadyBid", is(true));

        // Dashboard del transportista
        as(carrier).get("/api/v1/dashboard/summary").then().statusCode(200)
                .body("data.returnLoads", is(2)).body("data.topReturnLoads", hasSize(2))
                .body("data.topReturnLoads[0].requestId", is(juliaca)).body("data.topReturnLoads[0].originCity", is("JULIACA"))
                .body("data.topReturnLoads[0].weightKg", num(2000)).body("data.topReturnLoads[0].matchScore", is(96));
        // PYME y otro transportista: nada
        as(pyme).get("/api/v1/return-loads").then().statusCode(403);
        as(register("CARRIER", "Retorno Otro")).get("/api/v1/return-loads").then().body("data", hasSize(0));

        // En tránsito también cuenta (sugerencia anticipada)
        Flows.Awarded transit = Flows.inTransit("Retorno Transito");
        // RN02 por fecha: el vehículo queda libre tras la ventana del envío (recojo en 2 días + 3 de viaje = día 5), o sea el 6.º
        publishRoute(pyme, "JULIACA", "LIMA", 1500, 7).statusCode(201);
        as(transit.carrier()).get("/api/v1/return-loads").then().body("data", hasSize(greaterThanOrEqualTo(1)))
                .body("data[0].shipmentDelivered", is(false));
    }
}
