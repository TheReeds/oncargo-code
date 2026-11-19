package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.reports.application.ReportService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Reportes, CSV y documentos internos (2.11, V24): usan el precio final (acordado + ajustes aceptados) con IGV y la
 * comisión de la tasa guardada; cada parte ve solo sus envíos y la PYME nunca la comisión.
 */
@QuarkusTest
class ReportsFlowTest {

    @Inject AuthService auth;

    private Session operator() {
        var r = auth.provisionOperator("LOGYX Reportes " + System.nanoTime() % 1000, Api.newRuc(), "Operador Reportes",
                "oprep" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    private static BigDecimal d(Object v) { return new BigDecimal(String.valueOf(v)); }

    @Test
    void losReportesCuadranConElPrecioFinalLaComisionYElIgv() {
        Flows.Awarded a = Flows.delivered("Reportes");
        String id = a.shipmentId();
        String adj = as(a.carrier()).body(Map.of("type", "TOLL", "amount", 236)).post("/api/v1/shipments/" + id
                + "/adjustments").then().statusCode(201).extract().path("data.id");
        as(a.pyme()).body(Map.of()).post("/api/v1/adjustments/" + adj + "/accept").then().statusCode(200);
        var ship = as(a.carrier()).get("/api/v1/shipments/" + id).then().statusCode(200).extract();
        BigDecimal finalPrice = d(ship.path("data.finalPrice"));
        BigDecimal rate = d(ship.path("data.finalPriceBreakdown.commissionRate"));
        BigDecimal net = finalPrice.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        BigDecimal commission = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commissionVat = commission.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, d(ship.path("data.finalPriceBreakdown.commission")).compareTo(commission));

        var pyme = as(a.pyme()).get("/api/v1/reports/overview").then().statusCode(200)
                .body("data.viewer", is("PYME")).body("data.totals.shipments", is(1))
                .body("data.totals.delivered", is(1))
                .body("data.totals.commission", nullValue()).body("data.totals.carrierNet", nullValue())
                .body("data.byCounterparty[0].key", is(a.carrier().orgId()))
                .body("data.byCounterparty[0].label", startsWith("Carrier Reportes"))
                .body("data.byVehicle", empty()).body("data.bidsByRoute", empty()).extract();
        assertEquals(0, finalPrice.compareTo(d(pyme.path("data.totals.finalTotal"))));
        assertEquals(0, new BigDecimal("236").compareTo(d(pyme.path("data.totals.adjustmentsTotal"))));
        assertEquals(0, net.compareTo(d(pyme.path("data.totals.netOfVat"))));
        BigDecimal suggested = d(pyme.path("data.totals.suggestedTotal"));
        BigDecimal agreed = d(pyme.path("data.totals.agreedTotal"));
        assertEquals(0, suggested.subtract(agreed).compareTo(d(pyme.path("data.totals.savings"))),
                "ahorro = sugerido − acordado");

        var carrier = as(a.carrier()).get("/api/v1/reports/overview").then().statusCode(200)
                .body("data.viewer", is("CARRIER")).body("data.totals.shipments", is(1))
                .body("data.byVehicle", hasSize(1)).body("data.bidsByRoute[0].bids", is(1))
                .body("data.bidsByRoute[0].won", is(1)).extract();
        assertEquals(0, commission.compareTo(d(carrier.path("data.totals.commission"))));
        assertEquals(0, commissionVat.compareTo(d(carrier.path("data.totals.commissionVat"))));
        assertEquals(0, finalPrice.subtract(commission).subtract(commissionVat)
                .compareTo(d(carrier.path("data.totals.carrierNet"))));
        assertEquals(0, new BigDecimal("0.25").compareTo(d(carrier.path("data.totals.occupancy"))),
                "2 000 kg en un vehículo de 8 000 kg");

        Session op = operator();
        var opView = as(op).get("/api/v1/reports/overview").then().statusCode(200).body("data.viewer", is("OPERATOR"))
                .extract();
        assertTrue(((Number) opView.path("data.totals.shipments")).intValue() >= 1);
        assertNotNull(opView.path("data.totals.commission"));

        // Otra PYME no ve nada de esto
        Session other = register("PYME", "Reportes ajena");
        as(other).get("/api/v1/reports/overview").then().statusCode(200).body("data.totals.shipments", is(0));
        as(a.driver()).get("/api/v1/reports/overview").then().statusCode(403);

        // CSV: misma consulta; la PYME no recibe columnas de comisión
        String pymeCsv = as(a.pyme()).get("/api/v1/reports/export").then().statusCode(200)
                .contentType(containsString("text/csv")).extract().asString();
        assertFalse(pymeCsv.contains("Comisión"));
        assertTrue(pymeCsv.contains("Precio final (S/)"));
        assertTrue(pymeCsv.contains(finalPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        String carrierCsv = as(a.carrier()).get("/api/v1/reports/export").then().statusCode(200).extract().asString();
        assertTrue(carrierCsv.contains("IGV de la comisión (S/)"));
        assertTrue(carrierCsv.contains(commission.toPlainString()));

        // Documentos internos del mes de entrega
        String period = ReportService.today().toString().substring(0, 7);
        var liq = as(a.carrier()).get("/api/v1/reports/statements/" + period).then().statusCode(200)
                .body("data.kind", is("COMMISSION_STATEMENT")).body("data.number", startsWith("LIQ-"))
                .body("data.lines", hasSize(1)).body("data.disclaimer", containsString("SUNAT")).extract();
        assertEquals(0, commission.compareTo(d(liq.path("data.lines[0].commission"))));
        as(a.pyme()).get("/api/v1/reports/statements/" + period).then().statusCode(200)
                .body("data.kind", is("COST_SUMMARY")).body("data.lines", hasSize(1))
                .body("data.lines[0].commission", nullValue()).body("data.totals.commission", nullValue());
        as(other).get("/api/v1/reports/statements/" + period).then().statusCode(200).body("data.lines", empty());
        as(op).get("/api/v1/reports/statements/" + period).then().statusCode(400).body("error.field", is("organizationId"));
        as(op).get("/api/v1/reports/statements/" + period + "?organizationId=" + a.carrier().orgId()).then()
                .statusCode(200).body("data.lines", hasSize(1));
        as(a.pyme()).get("/api/v1/reports/statements/2999-01").then().statusCode(400);
        as(a.pyme()).get("/api/v1/reports/overview?from=2025-01-01&to=2026-12-31").then().statusCode(400);
    }
}
