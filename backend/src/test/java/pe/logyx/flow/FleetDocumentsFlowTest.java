package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.fleet.application.FleetDocumentService;
import pe.logyx.identity.application.AuthService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Roadmap 2.4: documentos de flota con vencimiento (privados del transportista), avisos de vencimiento, advertencia o
 * bloqueo al ofertar (parámetro del operador), sellos para la PYME y configuración por vehículo que solo restringe RN02.
 */
@QuarkusTest
class FleetDocumentsFlowTest {

    @Inject AuthService auth;
    @Inject FleetDocumentService documents;

    private static String upload(Session s, String path, String type, String expiresAt, byte[] bytes, String mime, int status) {
        var spec = raw(s).multiPart("file", "doc." + (mime.contains("pdf") ? "pdf" : "png"), bytes, mime)
                .multiPart("type", type);
        if (expiresAt != null) spec = spec.multiPart("expiresAt", expiresAt);
        var r = spec.post(path).then().statusCode(status);
        return status == 201 ? r.extract().path("data.id") : null;
    }

    private static String vdocs(String vehicleId) { return "/api/v1/vehicles/" + vehicleId + "/documents"; }

    private static String day(int plus) { return LocalDate.now().plusDays(plus).toString(); }

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Flota " + System.nanoTime() % 1000, Api.newRuc(), "Operador Flota", email,
                "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    @Test
    void subirReemplazaAlVigenteYSoloElDuenoLoVe() {
        Session carrier = register("CARRIER", "Docs Carrier");
        String v = vehicle(carrier, plate(), 8000);
        // Vencimiento obligatorio en el SOAT; archivo válido; tipo del dueño correcto
        upload(carrier, vdocs(v), "SOAT", null, PDF, "application/pdf", 400);
        upload(carrier, vdocs(v), "SOAT", day(100), "no soy un pdf".getBytes(), "application/pdf", 400);
        upload(carrier, vdocs(v), "DRIVER_LICENSE", day(100), PDF, "application/pdf", 400);
        String first = upload(carrier, vdocs(v), "SOAT", day(60), PDF, "application/pdf", 201);
        String second = upload(carrier, vdocs(v), "SOAT", day(365), PNG, "image/png", 201);
        upload(carrier, vdocs(v), "PROPERTY_CARD", null, PDF, "application/pdf", 201);        // sin vencimiento: ok

        as(carrier).get(vdocs(v)).then().statusCode(200).body("data", hasSize(3))
                .body("data.findAll { it.type == 'SOAT' && it.current }", hasSize(1))
                .body("data.find { it.id == '" + second + "' }.status", is("VALID"))
                .body("data.find { it.id == '" + first + "' }.current", is(false));
        as(carrier).get("/api/v1/fleet/documents").then().statusCode(200)
                .body("data.findAll { it.type == 'SOAT' }", hasSize(1));                       // el tablero solo trae vigentes
        byte[] bytes = as(carrier).get("/api/v1/fleet/documents/" + second + "/file").then().statusCode(200)
                .header("Content-Type", startsWith("image/png")).extract().asByteArray();
        assertArrayEquals(PNG, bytes);

        Session other = register("CARRIER", "Docs Intruso");
        as(other).get(vdocs(v)).then().statusCode(404);
        as(other).get("/api/v1/fleet/documents/" + second + "/file").then().statusCode(404);
        upload(other, vdocs(v), "SOAT", day(10), PDF, "application/pdf", 404);
        as(other).get("/api/v1/fleet/documents").then().body("data", hasSize(0));
        as(register("PYME", "Docs Pyme")).get(vdocs(v)).then().statusCode(403);
    }

    @Test
    void ofertarConDocumentoVencidoAdvierteYElOperadorPuedeBloquear() {
        Session carrier = register("CARRIER", "Docs Oferta");
        String v = vehicle(carrier, plate(), 8000);
        upload(carrier, vdocs(v), "SOAT", day(-3), PDF, "application/pdf", 201);              // vencido hace 3 días
        Session pyme = register("PYME", "Docs Oferta Pyme");
        String r = publishRoute(pyme, "LIMA", "JULIACA", 1000, 15).statusCode(201).extract().path("data.id");

        as(carrier).get("/api/v1/shipment-requests/" + r + "/vehicle-eligibility").then().statusCode(200)
                .body("data.vehicles[0].eligible", is(true))
                .body("data.vehicles[0].warnings.code", hasItem("LOGYX-FLT-007"));
        Session op = operator();
        as(op).body(Map.of("value", "1", "reason", "Prueba de bloqueo de documentos")).put("/api/v1/operator/parameters/fleet_docs_block")
                .then().statusCode(200);
        try {
            as(carrier).get("/api/v1/shipment-requests/" + r + "/vehicle-eligibility").then().statusCode(200)
                    .body("data.vehicles[0].eligible", is(false)).body("data.vehicles[0].blockerCode", is("LOGYX-FLT-007"));
            float floor = as(carrier).get("/api/v1/shipment-requests/" + r).then().extract().path("data.priceFloor");
            as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 100)).post("/api/v1/shipment-requests/" + r + "/bids")
                    .then().statusCode(409).body("error.code", is("LOGYX-FLT-007")).body("error.field", is("vehicleId"));
            // Con el documento renovado ya puede ofertar
            upload(carrier, vdocs(v), "SOAT", day(300), PDF, "application/pdf", 201);
            as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 100)).post("/api/v1/shipment-requests/" + r + "/bids")
                    .then().statusCode(201);
        } finally {
            as(op).body(Map.of("value", "0", "reason", "Restaura la advertencia")).put("/api/v1/operator/parameters/fleet_docs_block")
                    .then().statusCode(200);
        }
    }

    @Test
    void advertenciaNoBloqueaYLosSellosLlegaALaPyme() {
        Session carrier = register("CARRIER", "Docs Sellos");
        String vOk = vehicle(carrier, plate(), 8000);
        String vVencido = vehicle(carrier, plate(), 8000);
        upload(carrier, vdocs(vOk), "SOAT", day(200), PDF, "application/pdf", 201);
        upload(carrier, vdocs(vOk), "TECHNICAL_INSPECTION", day(10), PDF, "application/pdf", 201);   // vence antes del recojo
        upload(carrier, vdocs(vVencido), "SOAT", day(-1), PDF, "application/pdf", 201);
        Session pyme = register("PYME", "Docs Sellos Pyme");
        String r1 = publishRoute(pyme, "LIMA", "JULIACA", 1000, 30).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(pyme, "LIMA", "JULIACA", 1000, 30).statusCode(201).extract().path("data.id");

        float f1 = as(carrier).get("/api/v1/shipment-requests/" + r1).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", vOk, "amount", Math.ceil(f1) + 100)).post("/api/v1/shipment-requests/" + r1 + "/bids")
                .then().statusCode(201).body("data.warnings.code", hasItem("LOGYX-FLT-008"))
                .body("data.soatValidUntil", is(day(200))).body("data.inspectionValidUntil", is(day(10)));
        float f2 = as(carrier).get("/api/v1/shipment-requests/" + r2).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", vVencido, "amount", Math.ceil(f2) + 100)).post("/api/v1/shipment-requests/" + r2 + "/bids")
                .then().statusCode(201).body("data.warnings.code", hasItem("LOGYX-FLT-007"));

        // La PYME ve los sellos (no los archivos ni los documentos): vigente con fecha, o sin sello
        as(pyme).get("/api/v1/shipment-requests/" + r1 + "/bids").then().statusCode(200)
                .body("data[0].soatValidUntil", is(day(200))).body("data[0].inspectionValidUntil", is(day(10)));
        as(pyme).get("/api/v1/shipment-requests/" + r2 + "/bids").then().statusCode(200)
                .body("data[0].soatValidUntil", nullValue()).body("data[0].inspectionValidUntil", nullValue());
    }

    @Test
    void elBarridoAvisaUnaVezPorUmbralAlTransportista() {
        Session carrier = register("CARRIER", "Docs Barrido");
        String v = vehicle(carrier, plate(), 8000);
        upload(carrier, vdocs(v), "SOAT", day(5), PDF, "application/pdf", 201);               // entre 7 y 0 días: umbral 7
        upload(carrier, vdocs(v), "MTC_PERMIT", day(120), PDF, "application/pdf", 201);        // lejos: sin aviso
        int before = unread(carrier);
        documents.sweepExpiring(FleetDocumentService.today());
        assertEquals(before + 1, unread(carrier));
        as(carrier).get("/api/v1/notifications").then().statusCode(200)
                .body("data[0].type", is("FLEET_DOCUMENT_EXPIRING")).body("data[0].relatedType", is("VEHICLE"))
                .body("data[0].relatedId", is(v)).body("data[0].title", containsString("SOAT"))
                .body("data[0].title", containsString("5 días"));
        documents.sweepExpiring(FleetDocumentService.today());                                 // idempotente por umbral
        assertEquals(before + 1, unread(carrier));
        // El día que vence: umbral 0, un aviso más
        documents.sweepExpiring(LocalDate.now().plusDays(5));
        assertEquals(before + 2, unread(carrier));
    }

    private static int unread(Session s) {
        return as(s).get("/api/v1/notifications/unread-count").then().statusCode(200).extract().path("data.unread");
    }

    @Test
    void laConfiguracionDelVehiculoSoloRestringeLaMatriz() {
        Session carrier = register("CARRIER", "Docs Config");
        String v = vehicle(carrier, plate(), 8000);                                            // camión mediano
        // No amplía: un camión mediano no lleva perecederos
        as(carrier).body(Map.of("acceptedCargoTypes", List.of("PERISHABLE"))).put("/api/v1/vehicles/" + v + "/config")
                .then().statusCode(400).body("error.code", is("LOGYX-FLT-009"));
        as(carrier).body(Map.of("equipment", List.of("GPS", "RAMP"), "acceptedCargoTypes", List.of("FRAGILE")))
                .put("/api/v1/vehicles/" + v + "/config").then().statusCode(200)
                .body("data.equipment", containsInAnyOrder("GPS", "RAMP")).body("data.acceptedCargoTypes", contains("FRAGILE"));
        as(carrier).get("/api/v1/vehicles").then().body("data[0].acceptedCargoTypes", contains("FRAGILE"));

        Session pyme = register("PYME", "Docs Config Pyme");
        String general = publishRoute(pyme, "LIMA", "JULIACA", 1000, 20).statusCode(201).extract().path("data.id");
        as(carrier).get("/api/v1/shipment-requests/" + general + "/vehicle-eligibility").then()
                .body("data.vehicles[0].eligible", is(false)).body("data.vehicles[0].blockerCode", is("LOGYX-CAP-003"));
        float floor = as(carrier).get("/api/v1/shipment-requests/" + general).then().extract().path("data.priceFloor");
        as(carrier).body(Map.of("vehicleId", v, "amount", Math.ceil(floor) + 50)).post("/api/v1/shipment-requests/" + general + "/bids")
                .then().statusCode(400).body("error.code", is("LOGYX-CAP-003"));
        // Vaciar la lista quita la restricción
        as(carrier).body(Map.of("equipment", List.of(), "acceptedCargoTypes", List.of())).put("/api/v1/vehicles/" + v + "/config")
                .then().statusCode(200).body("data.acceptedCargoTypes", hasSize(0));
        bid(carrier, general, v, 50);
        as(register("CARRIER", "Docs Config Ajeno")).body(Map.of("equipment", List.of())).put("/api/v1/vehicles/" + v + "/config")
                .then().statusCode(404);
    }

    @Test
    void licenciaYDniDelConductor() {
        Session carrier = register("CARRIER", "Docs Conductor");
        String[] d = driver(carrier, "Pedro Quispe");
        as(carrier).body(Map.of("licenseCategory", "A-IIb", "dni", "12345678")).patch("/api/v1/drivers/" + d[0])
                .then().statusCode(200).body("data.licenseCategory", is("A-IIB")).body("data.dni", is("12345678"));
        as(carrier).body(Map.of("dni", "123")).patch("/api/v1/drivers/" + d[0]).then().statusCode(400);
        upload(carrier, "/api/v1/drivers/" + d[0] + "/documents", "DRIVER_LICENSE", null, PDF, "application/pdf", 400);
        upload(carrier, "/api/v1/drivers/" + d[0] + "/documents", "SOAT", day(100), PDF, "application/pdf", 400);
        upload(carrier, "/api/v1/drivers/" + d[0] + "/documents", "DRIVER_LICENSE", day(400), PDF, "application/pdf", 201);
        as(carrier).get("/api/v1/drivers").then().statusCode(200)
                .body("data[0].licenseExpiresAt", is(day(400))).body("data[0].licenseCategory", is("A-IIB"));
        as(carrier).get("/api/v1/drivers/" + d[0] + "/documents").then().body("data", hasSize(1))
                .body("data[0].typeLabel", is("Licencia de conducir"));
    }
}
