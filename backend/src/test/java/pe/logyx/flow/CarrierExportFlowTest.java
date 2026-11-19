package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import pe.logyx.support.Api.Session;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/** Roadmap 1.5 (zona C): CSV de «Mis ofertas» y «Mis viajes» con las mismas reglas de RLS y filtros que los listados. */
@QuarkusTest
class CarrierExportFlowTest {

    private static String csv(Session s, String path) {
        return as(s).get(path).then().statusCode(200).header("Content-Type", containsString("text/csv"))
                .header("Content-Disposition", containsString(".csv")).extract().asString();
    }

    @Test
    void misOfertasEnCsvConNetoEstimadoYFiltroPorEstado() {
        Session pyme = register("PYME", "Csv Pyme");
        Session carrier = register("CARRIER", "Csv Carrier");
        Session other = register("CARRIER", "Csv Otro");
        String v = vehicle(carrier, plate(), 8000);
        vehicle(other, plate(), 8000);
        String r1 = publishRoute(pyme, "LIMA", "JULIACA", 1000, 60).statusCode(201).extract().path("data.id");
        String r2 = publishRoute(pyme, "LIMA", "PUNO", 1000, 70).statusCode(201).extract().path("data.id");
        String b1 = bid(carrier, r1, v, 10);
        String b2 = bid(carrier, r2, v, 10);
        as(carrier).post("/api/v1/bids/" + b2 + "/withdraw").then().statusCode(200);
        assertNotNull(b1);

        String all = csv(carrier, "/api/v1/shipment-requests/my-bids/export");
        assertTrue(all.startsWith("﻿"), "BOM para Excel");
        assertTrue(all.contains("Neto estimado (S/)") && all.contains(";"), all);
        assertTrue(all.contains("Pendiente") && all.contains("Retirada"), all);
        long lines = all.lines().filter(l -> !l.isBlank()).count();
        assertEquals(3, lines, "cabecera + 2 ofertas");

        String pending = csv(carrier, "/api/v1/shipment-requests/my-bids/export?status=PENDING");
        assertEquals(2, pending.lines().filter(l -> !l.isBlank()).count());
        assertFalse(pending.contains("Retirada"));
        // Rango de fechas y ciudad
        String puno = csv(carrier, "/api/v1/shipment-requests/my-bids/export?destinationCity=PUNO");
        assertTrue(puno.contains("Puno") && !puno.contains("Juliaca"), puno);

        // Sin fuga: otro transportista no exporta las mías; la PYME no accede
        assertEquals(1, csv(other, "/api/v1/shipment-requests/my-bids/export").lines().filter(l -> !l.isBlank()).count(), "solo la cabecera");
        as(pyme).get("/api/v1/shipment-requests/my-bids/export").then().statusCode(403);
        as(carrier).get("/api/v1/shipment-requests/my-bids/export?status=NOPE").then().statusCode(400);
    }

    @Test
    void misViajesEnCsv() {
        Session carrier = register("CARRIER", "Csv Viajes");
        String v = vehicle(carrier, plate(), 9000);
        publishTrip(carrier, v, 5000, "PER_KG", 0.5, 5).statusCode(201);
        String body = csv(carrier, "/api/v1/trips/mine/export");
        assertTrue(body.startsWith("﻿") && body.contains("Reservas confirmadas") && body.contains("Por kg"), body);
        assertEquals(2, body.lines().filter(l -> !l.isBlank()).count());
        assertEquals(1, csv(carrier, "/api/v1/trips/mine/export?status=CANCELLED").lines().filter(l -> !l.isBlank()).count());
        as(carrier).get("/api/v1/trips/mine/export?fromDate=ayer").then().statusCode(400);
        as(register("PYME", "Csv Viajes Pyme")).get("/api/v1/trips/mine/export").then().statusCode(403);
    }
}
