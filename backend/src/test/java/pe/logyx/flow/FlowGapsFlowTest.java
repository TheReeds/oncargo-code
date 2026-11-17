package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.marketplace.application.CloseExpiredAuctionsUseCase;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.logyx.support.Api.*;

/**
 * Huecos P1 de la revisión 2026-11-16 (docs/reviews/2026-11-16-flow-gaps.md): F04/F05 verificación (RN01), F06
 * cancelación y reasignación, F07 subasta sin ofertas, F08 negociación del transportista (RF26), F09 matriz
 * vehículo-carga (RN02), F10 RN07 fuera del chat, F11 doble asignación, F13 estado final (RF55), F14 evidencia.
 */
@QuarkusTest
class FlowGapsFlowTest {

    @Inject AuthService auth;
    @Inject EntityManager em;
    @Inject SystemTx systemTx;
    @Inject CloseExpiredAuctionsUseCase closer;

    private Session operator() {
        var r = auth.provisionOperator("LOGYX Gaps " + System.nanoTime() % 1000, Api.newRuc(), "Operador Gaps",
                "opgaps" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    private static String reqPath(String id) { return "/api/v1/shipment-requests/" + id; }

    // ------------------------------------------------------------------ F04/F05 · RN01 verificación por el operador

    @Test
    void rn01SoloOrganizacionesVerificadasPorElOperadorPublicanYOfertan() {
        Session op = operator();
        Session pyme = registerPending("PYME", "Verif Pyme");
        Session carrier = registerPending("CARRIER", "Verif Carrier");

        // El dígito verificador ya no da el sello
        as(pyme).get("/api/v1/auth/me").then().statusCode(200).body("data.rucVerified", is(false))
                .body("data.rucValid", is(true)).body("data.verificationStatus", is("PENDING"));
        as(pyme).get("/api/v1/organizations/" + pyme.orgId()).then().body("data.rucVerified", is(false))
                .body("data.verificationStatus", is("PENDING"));

        // RN01 bloquea publicar, ofertar y publicar viajes
        publish(pyme, 1000).statusCode(403).body("error.code", is("LOGYX-ORG-003"));
        String vehicle = vehicle(carrier, plate(), 8000);
        publishTrip(carrier, vehicle, 5000, "PER_KG", 0.5, 3).statusCode(403).body("error.code", is("LOGYX-ORG-003"));
        Session verifiedPyme = register("PYME", "Verif Pyme OK");
        String req = publish(verifiedPyme, 1000).statusCode(201).extract().path("data.id");
        as(carrier).body(Map.of("vehicleId", vehicle, "amount", 99999)).post(reqPath(req) + "/bids").then()
                .statusCode(403).body("error.code", is("LOGYX-ORG-003"));

        // Bandeja del operador (solo operador)
        as(pyme).get("/api/v1/operator/organizations").then().statusCode(403);
        as(op).get("/api/v1/operator/organizations?size=100&type=PYME").then().statusCode(200)
                .body("data.id", hasItem(pyme.orgId())).body("data.id", not(hasItem(carrier.orgId())))
                .body("data.find { it.id == '" + pyme.orgId() + "' }.rucValid", is(true));
        as(op).get("/api/v1/operator/organizations?status=NOPE").then().statusCode(400);

        // Rechazar exige motivo; la organización ve el motivo y sigue bloqueada
        as(op).body(Map.of()).post("/api/v1/operator/organizations/" + pyme.orgId() + "/reject").then().statusCode(400);
        as(op).body(Map.of("reason", "El RUC figura como baja de oficio"))
                .post("/api/v1/operator/organizations/" + pyme.orgId() + "/reject").then().statusCode(200)
                .body("data.verificationStatus", is("REJECTED"));
        as(pyme).get("/api/v1/auth/me").then().body("data.verificationNote", is("El RUC figura como baja de oficio"));
        publish(pyme, 1000).statusCode(403).body("error.message", containsString("baja de oficio"));

        // Verificar habilita y avisa por el outbox
        as(op).body(Map.of("note", "RUC activo y habido")).post("/api/v1/operator/organizations/" + pyme.orgId()
                + "/verify").then().statusCode(200).body("data.verificationStatus", is("VERIFIED"))
                .body("data.rucVerified", is(true));
        as(op).post("/api/v1/operator/organizations/" + carrier.orgId() + "/verify").then().statusCode(200);
        publish(pyme, 1000).statusCode(201).body("data.requesterRucVerified", is(true));
        as(carrier).body(Map.of("vehicleId", vehicle, "amount", 99999)).post(reqPath(req) + "/bids").then().statusCode(201)
                .body("data.carrierRucVerified", is(true));
        as(pyme).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'ORGANIZATION_VERIFICATION' }.relatedType", is("ORGANIZATION"));

        // Suspender bloquea siempre (también aceptar ofertas)
        as(op).body(Map.of("reason", "Reclamos por cobros fuera de la plataforma"))
                .post("/api/v1/operator/organizations/" + verifiedPyme.orgId() + "/suspend").then().statusCode(200)
                .body("data.verificationStatus", is("SUSPENDED"));
        publish(verifiedPyme, 1000).statusCode(403).body("error.code", is("LOGYX-ORG-004"));
        String bidId = as(verifiedPyme).get(reqPath(req) + "/bids").then().extract().path("data[0].id");
        as(verifiedPyme).post("/api/v1/bids/" + bidId + "/accept").then().statusCode(403)
                .body("error.code", is("LOGYX-ORG-004"));

        // Las cuentas de operador no se verifican ni suspenden; una PYME no decide verificaciones
        as(op).body(Map.of("reason", "x")).post("/api/v1/operator/organizations/" + op.orgId() + "/suspend").then()
                .statusCode(409).body("error.code", is("LOGYX-ORG-005"));
        as(pyme).post("/api/v1/operator/organizations/" + pyme.orgId() + "/verify").then().statusCode(403);
    }

    // ------------------------------------------------------------------ F09 · RN02 matriz vehículo-carga

    @Test
    void rn02ElTipoYElVolumenDelVehiculoDebenSerAptosParaLaCarga() {
        Session pyme = register("PYME", "Matriz Pyme");
        Session carrier = register("CARRIER", "Matriz Carrier");
        String truck = vehicleOf(carrier, "TRUCK_MEDIUM", 8000, 30);
        String reefer = vehicleOf(carrier, "REFRIGERATED", 6000, 30);
        String platform = vehicleOf(carrier, "PLATFORM", 15000, null);

        String perishable = publishCargo(pyme, "PERISHABLE", 1000, null, 3).statusCode(201)
                .body("data.compatibleVehicleTypes", contains("REFRIGERATED")).extract().path("data.id");
        float floor = as(carrier).get(reqPath(perishable)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", truck, "amount", Math.ceil(floor) + 10)).post(reqPath(perishable) + "/bids")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-003")).body("error.field", is("vehicleId"))
                .body("error.message", containsString("refrigerado"));
        bid(carrier, perishable, reefer, 10);

        String oversized = publishCargo(pyme, "OVERSIZED", 7000, null, 3).statusCode(201).extract().path("data.id");
        floor = as(carrier).get(reqPath(oversized)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", truck, "amount", Math.ceil(floor) + 10)).post(reqPath(oversized) + "/bids")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-003"));
        bid(carrier, oversized, platform, 10);

        // Volumen declarado mayor al del vehículo
        String bulky = publishCargo(pyme, "GENERAL", 1000, 60, 3).statusCode(201).extract().path("data.id");
        floor = as(carrier).get(reqPath(bulky)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", truck, "amount", Math.ceil(floor) + 10)).post(reqPath(bulky) + "/bids")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-004"));
        bid(carrier, bulky, platform, 10);   // sin volumen declarado: no se puede comprobar
    }

    // ------------------------------------------------------------------ F10 · RN07 en todos los textos libres

    @Test
    void rn07BloqueaDatosDeContactoFueraDelChat() {
        Session pyme = register("PYME", "Rn07 Pyme");
        Session carrier = register("CARRIER", "Rn07 Carrier");
        String v = vehicle(carrier, plate(), 8000);

        // Descripción de la carga (la ven todos los transportistas)
        as(pyme).body(Map.of("originCity", "LIMA", "originAddress", "Av. Argentina 1234", "destinationCity", "PUNO",
                        "destinationAddress", "Jr. Lima 1", "cargoType", "GENERAL",
                        "cargoDescription", "Cajas, coordinar al 987654321", "weightKg", 1000,
                        "pickupDate", LocalDate.now().plusDays(3).toString()))
                .post("/api/v1/shipment-requests").then().statusCode(400)
                .body("error.code", is("LOGYX-NEG-001")).body("error.field", is("cargoDescription"));

        // Notas de la oferta
        String req = publish(pyme, 1000).statusCode(201).extract().path("data.id");
        float floor = as(carrier).get(reqPath(req)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 5, "notes", "escríbeme a juan@gmail.com"))
                .post(reqPath(req) + "/bids").then().statusCode(400)
                .body("error.code", is("LOGYX-NEG-001")).body("error.field", is("notes"));

        // Notas del viaje y motivo de rechazo de una reserva
        as(carrier).body(Map.of("originCity", "LIMA", "destinationCity", "JULIACA", "departureAt", departure(4),
                        "vehicleId", v, "capacityKg", 5000, "pricingMode", "PER_KG", "rate", 0.5,
                        "notes", "Reservas por WhatsApp +51 987 654 321"))
                .post("/api/v1/trips").then().statusCode(400).body("error.field", is("notes"));
        String trip = publishTrip(carrier, v, 5000, "PER_KG", 0.5, 4).statusCode(201).extract().path("data.id");
        String booking = bookTrip(pyme, trip, 500).statusCode(201).extract().path("data.id");
        as(carrier).body(Map.of("reason", "Llámame al 951-222-333")).post("/api/v1/trip-bookings/" + booking + "/reject")
                .then().statusCode(400).body("error.code", is("LOGYX-NEG-001")).body("error.field", is("reason"));

        // Incidencia (descripción y comentario) y reseña pública
        Flows.Awarded d = Flows.delivered("Rn07 Envio");
        as(d.pyme()).body(Map.of("category", "OTHER", "description", "Contáctame en www.miempresa.pe"))
                .post("/api/v1/shipments/" + d.shipmentId() + "/incidents").then().statusCode(400)
                .body("error.field", is("description"));
        String inc = as(d.pyme()).body(Map.of("category", "OTHER", "description", "Faltó una caja"))
                .post("/api/v1/shipments/" + d.shipmentId() + "/incidents").then().statusCode(201).extract().path("data.id");
        as(d.carrier()).body(Map.of("body", "Mi número es 987 654 321")).post("/api/v1/incidents/" + inc + "/comments")
                .then().statusCode(400).body("error.field", is("body"));
        as(d.pyme()).body(Map.of("punctuality", 5, "cargoCondition", 5, "communication", 5,
                        "comment", "Excelente, sigue a @transportes_sur"))
                .post("/api/v1/shipments/" + d.shipmentId() + "/reviews").then().statusCode(400)
                .body("error.code", is("LOGYX-NEG-001")).body("error.field", is("comment"));
    }

    // ------------------------------------------------------------------ F11 · doble asignación de conductor y vehículo

    @Test
    void f11UnVehiculoOConductorNoSeComprometeEnDosEnviosSolapados() {
        Session carrier = register("CARRIER", "Solape Carrier");
        String v = vehicle(carrier, plate(), 8000);
        String other = vehicle(carrier, plate(), 8000);
        Session p1 = register("PYME", "Solape Pyme 1");
        Session p2 = register("PYME", "Solape Pyme 2");
        String r1 = publishRoute(p1, "LIMA", "JULIACA", 1000, 5).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(p2, "LIMA", "PUNO", 1000, 6).statusCode(201).extract().path("data.id");
        String r3 = publishRoute(p2, "LIMA", "CUSCO", 1000, 7).statusCode(201).extract().path("data.id");
        String far = publishRoute(p2, "LIMA", "AREQUIPA", 1000, 30).statusCode(201).extract().path("data.id");

        // Ofertar no reserva el vehículo: ofertas con el mismo vehículo en dos solicitudes solapadas
        String b1 = bid(carrier, r1, v, 10);
        String b2 = bid(carrier, r2, v, 10);
        String s1 = as(p1).post("/api/v1/bids/" + b1 + "/accept").then().statusCode(200).extract().path("data.shipmentId");
        // Aceptar la segunda: el vehículo ya está comprometido (Lima → Juliaca ocupa 3 días)
        as(p2).post("/api/v1/bids/" + b2 + "/accept").then().statusCode(409)
                .body("error.code", is("LOGYX-CAP-005")).body("error.field", is("vehicleId"));
        // Y ofertar de nuevo con ese vehículo en fechas solapadas ya no se puede; en fechas lejanas, sí
        float floor = as(carrier).get(reqPath(r3)).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 10)).post(reqPath(r3) + "/bids").then()
                .statusCode(409).body("error.code", is("LOGYX-CAP-005"));
        String b3 = bid(carrier, r3, other, 10);
        bid(carrier, far, v, 10);

        // Conductor: no puede tomar dos envíos solapados
        String[] driver = driver(carrier, "Chofer Solape");
        as(carrier).body(Map.of("driverId", driver[0])).put("/api/v1/shipments/" + s1 + "/driver").then().statusCode(200);
        String s3 = as(p2).post("/api/v1/bids/" + b3 + "/accept").then().statusCode(200).extract().path("data.shipmentId");
        as(carrier).body(Map.of("driverId", driver[0])).put("/api/v1/shipments/" + s3 + "/driver").then().statusCode(409)
                .body("error.code", is("LOGYX-SHP-004")).body("error.field", is("driverId"));
        String[] driver2 = driver(carrier, "Chofer Libre");
        as(carrier).body(Map.of("driverId", driver2[0])).put("/api/v1/shipments/" + s3 + "/driver").then().statusCode(200);
        // Reasignar el mismo conductor a su propio envío no choca consigo mismo
        as(carrier).body(Map.of("driverId", driver[0])).put("/api/v1/shipments/" + s1 + "/driver").then().statusCode(200);
    }

    @Test
    void f11DosAdjudicacionesConcurrentesDelMismoVehiculoSoloPasaUna() throws Exception {
        Session carrier = register("CARRIER", "Carrera Carrier");
        String v = vehicle(carrier, plate(), 8000);
        Session p1 = register("PYME", "Carrera Pyme 1");
        Session p2 = register("PYME", "Carrera Pyme 2");
        String r1 = publishRoute(p1, "LIMA", "JULIACA", 1000, 8).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(p2, "LIMA", "JULIACA", 1000, 8).statusCode(201).extract().path("data.id");
        String b1 = bid(carrier, r1, v, 10);
        String b2 = bid(carrier, r2, v, 10);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> as(p1).post("/api/v1/bids/" + b1 + "/accept").then().extract().statusCode(),
                    () -> as(p2).post("/api/v1/bids/" + b2 + "/accept").then().extract().statusCode());
            List<Integer> codes = pool.invokeAll(calls).stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new IllegalStateException(e); }
            }).sorted().toList();
            assertEquals(List.of(200, 409), codes, "el bloqueo por vehículo debe dejar pasar solo una adjudicación");
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ F06 · cancelar y reasignar

    @Test
    void f06LaPymeCancelaElEnvioAntesDelRecojoYRepublica() {
        Flows.Awarded a = Flows.awarded("Cancelar Pyme");
        String sid = a.shipmentId();
        Session stranger = register("PYME", "Cancelar Ajena");

        as(a.pyme()).body(Map.of()).post("/api/v1/shipments/" + sid + "/cancel").then().statusCode(400);
        as(stranger).body(Map.of("reason", "x")).post("/api/v1/shipments/" + sid + "/cancel").then().statusCode(404);
        as(a.pyme()).body(Map.of("reason", "Llámame al 987654321")).post("/api/v1/shipments/" + sid + "/cancel")
                .then().statusCode(400).body("error.code", is("LOGYX-NEG-001"));
        as(a.pyme()).body(Map.of("reason", "El cliente final anuló el pedido")).post("/api/v1/shipments/" + sid + "/cancel")
                .then().statusCode(200).body("data.status", is("CANCELLED"))
                .body("data.cancellationReason", is("El cliente final anuló el pedido"))
                .body("data.cancelledByOrgId", is(a.pyme().orgId()));
        // La contraparte se entera (outbox) y la solicitud queda cancelada con el motivo
        as(a.carrier()).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'SHIPMENT_CANCELLED' }.body", containsString("anuló el pedido"));
        as(a.pyme()).get(reqPath(a.requestId())).then().body("data.status", is("CANCELLED"))
                .body("data.cancelReason", is("El cliente final anuló el pedido"));
        // Ya cancelado: no se cancela ni reasigna
        as(a.carrier()).body(Map.of("reason", "x")).post("/api/v1/shipments/" + sid + "/cancel").then().statusCode(409)
                .body("error.code", is("LOGYX-SHP-003"));

        // F07/F06: republicar la carga
        String again = as(a.pyme()).body(Map.of()).post(reqPath(a.requestId()) + "/republish").then().statusCode(201)
                .body("data.status", is("PUBLISHED")).body("data.republishedFrom", is(a.requestId()))
                .body("data.auction.status", is("OPEN")).extract().path("data.id");
        assertTrue(!again.equals(a.requestId()));
        as(a.pyme()).body(Map.of()).post(reqPath(again) + "/republish").then().statusCode(409)
                .body("error.code", is("LOGYX-REQ-004"));
    }

    @Test
    void f06ElTransportistaCambiaVehiculoYConductorYSoloElOperadorCancelaEnRuta() {
        Flows.Awarded a = Flows.awarded("Reasignar");
        String sid = a.shipmentId();
        String small = vehicleOf(a.carrier(), "VAN", 1000, null);
        String big = vehicle(a.carrier(), plate(), 9000);

        // Antes del recojo: cambio de vehículo sin motivo; RN02 aplica al vehículo nuevo
        as(a.carrier()).body(Map.of("vehicleId", small)).put("/api/v1/shipments/" + sid + "/vehicle").then()
                .statusCode(400).body("error.code", is("LOGYX-CAP-001"));
        String plate = as(a.carrier()).body(Map.of("vehicleId", big)).put("/api/v1/shipments/" + sid + "/vehicle")
                .then().statusCode(200).body("data.vehicleId", is(big)).extract().path("data.vehiclePlate");
        as(a.pyme()).get("/api/v1/shipments/" + sid).then().body("data.vehiclePlate", is(plate));
        as(a.pyme()).get("/api/v1/notifications").then()
                .body("data.find { it.title == 'Cambio de vehículo' }.body", containsString(plate));
        as(a.pyme()).body(Map.of("vehicleId", big)).put("/api/v1/shipments/" + sid + "/vehicle").then().statusCode(403);

        // Recojo: ya en ruta, cambiar exige motivo
        as(a.driver()).post(a.stop(a.pickupStopId(), "arrive")).then().statusCode(200);
        as(a.driver()).post(a.stop(a.pickupStopId(), "complete")).then().statusCode(200);
        String third = vehicle(a.carrier(), plate(), 9000);
        as(a.carrier()).body(Map.of("vehicleId", third)).put("/api/v1/shipments/" + sid + "/vehicle").then()
                .statusCode(400).body("error.code", is("LOGYX-SHP-006")).body("error.field", is("reason"));
        as(a.carrier()).body(Map.of("vehicleId", third, "reason", "Avería en Ayaviri, trasbordo"))
                .put("/api/v1/shipments/" + sid + "/vehicle").then().statusCode(200);
        as(a.carrier()).get("/api/v1/vehicles").then()
                .body("data.find { it.id == '" + big + "' }.status", is("AVAILABLE"))
                .body("data.find { it.id == '" + third + "' }.status", is("IN_ROUTE"));
        // Relevo de conductor en ruta (antes daba LOGYX-SHP-001)
        String[] relief = driver(a.carrier(), "Chofer Relevo");
        as(a.carrier()).body(Map.of("driverId", relief[0])).put("/api/v1/shipments/" + sid + "/driver").then()
                .statusCode(400).body("error.code", is("LOGYX-SHP-006"));
        as(a.carrier()).body(Map.of("driverId", relief[0], "reason", "Relevo por horas de manejo"))
                .put("/api/v1/shipments/" + sid + "/driver").then().statusCode(200).body("data.driverId", is(relief[0]));
        as(a.driver()).get("/api/v1/shipments/" + sid).then().statusCode(404);     // el conductor anterior ya no lo ve

        // En tránsito: las partes no cancelan; el operador sí, y libera la flota
        as(a.pyme()).body(Map.of("reason", "Ya no lo necesito")).post("/api/v1/shipments/" + sid + "/cancel").then()
                .statusCode(409).body("error.code", is("LOGYX-SHP-003"));
        Session op = operator();
        as(op).body(Map.of("reason", "Carga retenida por SUTRAN")).post("/api/v1/shipments/" + sid + "/cancel").then()
                .statusCode(200).body("data.status", is("CANCELLED"));
        as(a.carrier()).get("/api/v1/vehicles").then().body("data.find { it.id == '" + third + "' }.status", is("AVAILABLE"));
        as(a.pyme()).get("/api/v1/notifications").then().body("data.type", hasItem("SHIPMENT_CANCELLED"));
        as(a.carrier()).get("/api/v1/notifications").then().body("data.type", hasItem("SHIPMENT_CANCELLED"));
    }

    @Test
    void f06UnaIncidenciaActivaImpideQueLasPartesCancelen() {
        Flows.Awarded a = Flows.awarded("Cancelar Incidencia");
        as(a.pyme()).body(Map.of("category", "OTHER", "description", "El vehículo no llegó"))
                .post("/api/v1/shipments/" + a.shipmentId() + "/incidents").then().statusCode(201);
        as(a.carrier()).body(Map.of("reason", "No podemos atender")).post("/api/v1/shipments/" + a.shipmentId() + "/cancel")
                .then().statusCode(409).body("error.code", is("LOGYX-TRK-005"));
    }

    @Test
    void f06CancelarElEnvioDeUnaReservaDevuelveLaCapacidadDelViaje() {
        Session carrier = register("CARRIER", "Viaje Cancelado");
        Session pyme = register("PYME", "Viaje Pyme");
        String v = vehicle(carrier, plate(), 8000);
        String trip = publishTrip(carrier, v, 6000, "PER_KG", 0.5, 4).statusCode(201).extract().path("data.id");
        String booking = bookTrip(pyme, trip, 1500).statusCode(201).extract().path("data.id");
        String sid = as(carrier).post("/api/v1/trip-bookings/" + booking + "/confirm").then().statusCode(200)
                .extract().path("data.shipmentId");
        as(carrier).get("/api/v1/trips/" + trip).then().body("data.remainingKg", num(4500));
        as(carrier).body(Map.of("reason", "Unidad en mantenimiento")).post("/api/v1/shipments/" + sid + "/cancel")
                .then().statusCode(200);
        as(pyme).get("/api/v1/trip-bookings/mine").then()
                .body("data.find { it.id == '" + booking + "' }.status", is("CANCELLED"));
        as(carrier).get("/api/v1/trips/" + trip).then().body("data.remainingKg", num(6000));
    }

    // ------------------------------------------------------------------ F07 · subasta que cierra sin ofertas

    @Test
    void f07SubastaSinOfertasAvisaPermiteReabrirYElOperadorActuaSinEsperar24h() {
        Session op = operator();
        Session pyme = register("PYME", "Sin Ofertas Pyme");
        Session carrier = register("CARRIER", "Sin Ofertas Carrier");
        String v = vehicle(carrier, plate(), 8000);
        String req = publish(pyme, 1500).statusCode(201).extract().path("data.id");

        // Mientras la subasta está abierta (y < 24 h) el operador no asigna
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", v))
                .post("/api/v1/operator/requests/" + req + "/assign").then().statusCode(409)
                .body("error.code", is("LOGYX-OPS-001"));
        as(pyme).body(Map.of()).post(reqPath(req) + "/reopen").then().statusCode(409)
                .body("error.code", is("LOGYX-AUC-003"));

        // Vence la subasta (RN04 por tiempo) sin ofertas
        systemTx.run(() -> em.createNativeQuery("UPDATE auctions SET closes_at = now() - interval '1 minute' "
                + "WHERE request_id = ?1").setParameter(1, UUID.fromString(req)).executeUpdate());
        assertTrue(closer.closeExpired() >= 1);
        as(pyme).get(reqPath(req)).then().body("data.auction.status", is("CLOSED"))
                .body("data.auction.closeReason", is("TIMEOUT"));
        as(pyme).get("/api/v1/notifications").then()
                .body("data.find { it.relatedId == '" + req + "' && it.type == 'AUCTION_CLOSED' }.title",
                        is("Tu subasta cerró sin ofertas"));
        as(op).get("/api/v1/operator/sla-alerts").then().body("data.requestsWithoutBids.id", hasItem(req));
        as(op).get("/api/v1/notifications").then().body("data.find { it.relatedId == '" + req + "' }.type",
                is("AUCTION_CLOSED"));

        // La PYME la reabre (con otra fecha); vuelve al marketplace y acepta ofertas
        String newDate = LocalDate.now().plusDays(6).toString();
        as(pyme).body(Map.of("pickupDate", LocalDate.now().minusDays(1).toString())).post(reqPath(req) + "/reopen")
                .then().statusCode(400);
        as(pyme).body(Map.of("pickupDate", newDate)).post(reqPath(req) + "/reopen").then().statusCode(200)
                .body("data.auction.status", is("OPEN")).body("data.pickupDate", is(newDate));
        as(carrier).get("/api/v1/shipment-requests?size=100&originCity=LIMA&destinationCity=JULIACA&pickupFrom=" + newDate
                + "&pickupTo=" + newDate).then().body("data.id", hasItem(req));

        // Vuelve a cerrar sin ofertas: el operador puede asignar al instante (no hay hueco de ~23 h)
        systemTx.run(() -> em.createNativeQuery("UPDATE auctions SET closes_at = now() - interval '1 minute' "
                + "WHERE request_id = ?1").setParameter(1, UUID.fromString(req)).executeUpdate());
        closer.closeExpired();
        as(op).body(Map.of("carrierOrgId", carrier.orgId(), "vehicleId", v))
                .post("/api/v1/operator/requests/" + req + "/assign").then().statusCode(200)
                .body("data.assignedByOperator", is(true));
    }

    // ------------------------------------------------------------------ F08 · RF26 el transportista negocia y retira

    @Test
    void f08ElTransportistaContraofertaYLaPymeAceptaOResponde() {
        Flows.Bidding b = Flows.bidding("Contra Transportista");
        String co = "/api/v1/bids/" + b.bidId() + "/counter-offers";

        // RF26: el transportista propone (≥ piso) y la PYME la recibe
        String c1 = as(b.carrier()).body(Map.of("amount", b.above(200), "message", "Incluye estiba"))
                .post(co).then().statusCode(201).body("data.proposedByCarrier", is(true))
                .body("data.offeredByOrgId", is(b.carrier().orgId())).extract().path("data.id");
        as(b.carrier()).body(Map.of("amount", b.above(150))).post(co).then().statusCode(409)
                .body("error.code", is("LOGYX-NEG-004"));
        as(b.carrier()).body(Map.of("amount", b.floor() - 10)).post(co).then().statusCode(400)
                .body("error.code", is("LOGYX-BID-001"));
        as(b.pyme()).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'COUNTER_OFFER' }.relatedId", is(c1));
        // Solo la otra parte responde
        as(b.carrier()).post("/api/v1/counter-offers/" + c1 + "/accept").then().statusCode(403);

        // La PYME responde con otro monto: reemplaza la del transportista
        String c2 = as(b.pyme()).body(Map.of("amount", b.above(100))).post(co).then().statusCode(201)
                .body("data.proposedByCarrier", is(false)).extract().path("data.id");
        as(b.carrier()).get(co).then().body("data.find { it.id == '" + c1 + "' }.status", is("REJECTED"))
                .body("data.find { it.id == '" + c2 + "' }.status", is("PENDING"));

        // El transportista vuelve a contraofertar y la PYME acepta (RF28): el monto de la oferta cambia
        as(b.carrier()).post("/api/v1/counter-offers/" + c2 + "/reject").then().statusCode(200);
        String c3 = as(b.carrier()).body(Map.of("amount", b.above(150))).post(co).then().statusCode(201)
                .extract().path("data.id");
        as(b.pyme()).post("/api/v1/counter-offers/" + c3 + "/accept").then().statusCode(200)
                .body("data.status", is("ACCEPTED"));
        as(b.pyme()).get(reqPath(b.requestId()) + "/bids").then().body("data[0].amount", is(b.above(150)));
        as(b.carrier()).get("/api/v1/notifications").then()
                .body("data.find { it.title == 'Contra-oferta aceptada' }.relatedId", is(b.bidId()));
    }

    @Test
    void f08ElTransportistaRetiraSuOfertaConLaSubastaAbiertaYSeVencenSusContraofertas() {
        Flows.Bidding b = Flows.bidding("Retiro");
        Session other = register("CARRIER", "Retiro Otro");
        String co = as(b.pyme()).body(Map.of("amount", b.above(50))).post("/api/v1/bids/" + b.bidId() + "/counter-offers")
                .then().statusCode(201).extract().path("data.id");

        as(other).post("/api/v1/bids/" + b.bidId() + "/withdraw").then().statusCode(404);
        as(b.pyme()).post("/api/v1/bids/" + b.bidId() + "/withdraw").then().statusCode(403);
        as(b.carrier()).post("/api/v1/bids/" + b.bidId() + "/withdraw").then().statusCode(200)
                .body("data.status", is("WITHDRAWN"));
        as(b.carrier()).post("/api/v1/bids/" + b.bidId() + "/withdraw").then().statusCode(409)
                .body("error.code", is("LOGYX-BID-005"));
        // La PYME lo ve y se entera; la oferta ya no cuenta para RN04 ni se puede aceptar
        as(b.pyme()).get(reqPath(b.requestId())).then().body("data.auction.bidCount", is(0));
        as(b.pyme()).get("/api/v1/notifications").then().body("data.type", hasItem("BID_WITHDRAWN"));
        as(b.pyme()).post("/api/v1/bids/" + b.bidId() + "/accept").then().statusCode(409);
        // F40: su contra-oferta pendiente vence
        as(b.pyme()).get("/api/v1/bids/" + b.bidId() + "/counter-offers").then()
                .body("data.find { it.id == '" + co + "' }.status", is("EXPIRED"));
        // No vuelve a ofertar en esa subasta
        as(b.carrier()).body(Map.of("vehicleId", b.vehicleId(), "amount", b.above(10)))
                .post(reqPath(b.requestId()) + "/bids").then().statusCode(409).body("error.code", is("LOGYX-BID-003"))
                .body("error.message", containsString("Retiraste"));
    }

    @Test
    void f40AlAdjudicarOtraOfertaLasContraofertasPendientesVencen() {
        Flows.Bidding b = Flows.bidding("Huerfana");
        Session rival = register("CARRIER", "Huerfana Rival");
        String winner = bid(rival, b.requestId(), vehicle(rival, plate(), 8000), 400);
        String co = as(b.pyme()).body(Map.of("amount", b.above(80))).post("/api/v1/bids/" + b.bidId() + "/counter-offers")
                .then().statusCode(201).extract().path("data.id");
        as(b.pyme()).post("/api/v1/bids/" + winner + "/accept").then().statusCode(200);
        as(b.carrier()).get("/api/v1/bids/" + b.bidId() + "/counter-offers").then()
                .body("data.find { it.id == '" + co + "' }.status", is("EXPIRED"));
        as(b.carrier()).post("/api/v1/counter-offers/" + co + "/accept").then().statusCode(409);
        // Tras adjudicar, ya no se retira
        as(rival).post("/api/v1/bids/" + winner + "/withdraw").then().statusCode(409).body("error.code", is("LOGYX-BID-005"));
    }

    // ------------------------------------------------------------------ F13 · RF55 estado final del envío

    @Test
    void f13ElOperadorFijaElEstadoFinalDelEnvioAlResolver() {
        Session op = operator();
        // Carga extraviada en ruta → cancelado (la solicitud también; la flota se libera)
        Flows.Awarded lost = Flows.inTransit("Extravio");
        String i1 = as(lost.pyme()).body(Map.of("category", "LOST_CARGO", "description", "No llegó ninguna caja"))
                .post("/api/v1/shipments/" + lost.shipmentId() + "/incidents").then().statusCode(201).extract().path("data.id");
        as(op).body(Map.of("status", "CLOSED")).post("/api/v1/incidents/" + i1 + "/status").then().statusCode(400)
                .body("error.code", is("LOGYX-INC-003"));
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "Carga extraviada: se indemniza",
                        "shipmentOutcome", "CANCELLED"))
                .post("/api/v1/incidents/" + i1 + "/status").then().statusCode(200)
                .body("data.shipmentOutcome", is("CANCELLED"));
        as(lost.pyme()).get("/api/v1/shipments/" + lost.shipmentId()).then().body("data.status", is("CANCELLED"))
                .body("data.cancellationReason", is("Carga extraviada: se indemniza"));
        as(lost.pyme()).get(reqPath(lost.requestId())).then().body("data.status", is("CANCELLED"));
        as(lost.pyme()).get("/api/v1/notifications").then()
                .body("data.find { it.type == 'INCIDENT_RESOLVED' }.body", containsString("quedó cancelado"));

        // Entrega confirmada fuera de la app → entregado (paradas cerradas)
        Flows.Awarded late = Flows.inTransit("Entregado Op");
        String i2 = as(late.carrier()).body(Map.of("category", "DELAY", "description", "Sin señal en la ruta"))
                .post("/api/v1/shipments/" + late.shipmentId() + "/incidents").then().statusCode(201).extract().path("data.id");
        as(op).body(Map.of("status", "CLOSED", "resolutionNotes", "La PYME confirmó la recepción por teléfono",
                        "shipmentOutcome", "DELIVERED"))
                .post("/api/v1/incidents/" + i2 + "/status").then().statusCode(200);
        as(late.pyme()).get("/api/v1/shipments/" + late.shipmentId()).then().body("data.status", is("DELIVERED"))
                .body("data.deliveredAt", notNullValue()).body("data.stops.status", everyItem(is("COMPLETED")));

        // Un envío pendiente no se da por entregado
        Flows.Awarded pending = Flows.awarded("Pendiente Op");
        String i3 = as(pending.pyme()).body(Map.of("category", "OTHER", "description", "No confirman hora"))
                .post("/api/v1/shipments/" + pending.shipmentId() + "/incidents").then().statusCode(201).extract().path("data.id");
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "x", "shipmentOutcome", "DELIVERED"))
                .post("/api/v1/incidents/" + i3 + "/status").then().statusCode(409).body("error.code", is("LOGYX-INC-006"));
        as(op).body(Map.of("status", "RESOLVED", "resolutionNotes", "Se coordinó la hora", "shipmentOutcome", "RESUME"))
                .post("/api/v1/incidents/" + i3 + "/status").then().statusCode(200);
        as(pending.pyme()).get("/api/v1/shipments/" + pending.shipmentId()).then().body("data.status", is("PENDING"))
                .body("data.hasActiveIncident", is(false));
    }

    // ------------------------------------------------------------------ F14 · evidencia de entrega

    @Test
    void f14LaEvidenciaSoloLaSubeElConductorAsignadoEntreLlegadaYCierre() {
        Flows.Awarded a = Flows.inTransit("Evidencia");
        String sid = a.shipmentId();
        String stop = a.deliveryStopId();
        // Antes de llegar
        upload(a.driver(), sid, "DELIVERY_PHOTO", stop, "f.png", PNG, "image/png").statusCode(409)
                .body("error.code", is("LOGYX-DOC-005"));
        as(a.driver()).post(a.stop(stop, "arrive")).then().statusCode(200);
        // Ni la oficina del transportista ni la PYME; tampoco otro conductor de la misma empresa
        upload(a.carrier(), sid, "DELIVERY_PHOTO", stop, "f.png", PNG, "image/png").statusCode(403)
                .body("error.code", is("LOGYX-DOC-007"));
        upload(a.pyme(), sid, "SIGNATURE", stop, "f.png", PNG, "image/png").statusCode(403)
                .body("error.code", is("LOGYX-DOC-007"));
        String[] colleague = driver(a.carrier(), "Chofer Colega");
        Session other = login(colleague[1], "Chofer1234");
        upload(other, sid, "DELIVERY_PHOTO", stop, "f.png", PNG, "image/png").statusCode(404);
        // El conductor asignado, sí
        upload(a.driver(), sid, "DELIVERY_PHOTO", stop, "f.png", PNG, "image/png").statusCode(201);
        as(a.driver()).post(a.stop(stop, "complete")).then().statusCode(200);
        // Cerrada la entrega: inmutable (antes se podía fabricar evidencia después)
        upload(a.driver(), sid, "DELIVERY_PHOTO", stop, "f.png", PNG, "image/png").statusCode(409)
                .body("error.code", is("LOGYX-DOC-008"));
        upload(a.driver(), sid, "SIGNATURE", stop, "f.png", PNG, "image/png").statusCode(409);
        // Los demás documentos siguen permitidos tras la entrega (comprobante, guía)
        upload(a.pyme(), sid, "RECEIPT", null, "boleta.pdf", PDF, "application/pdf").statusCode(201);
    }
}
