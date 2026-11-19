package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
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
 * Comisión (ADR-0010): al adjudicar se guarda la tasa vigente en el envío; la comisión es sobre el valor de venta sin
 * IGV y lleva su propio IGV; la PYME ve el IGV desglosado y NO la comisión; un cambio posterior de la tasa no altera
 * el envío ya creado.
 */
@QuarkusTest
class CommissionFlowTest {

    @Inject AuthService auth;

    private Session operator() {
        String email = "operador" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Comision " + System.nanoTime() % 1000, Api.newRuc(), "Operador Comision",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    private static BigDecimal d(Object n) { return new BigDecimal(n.toString()); }

    @Test
    void alAdjudicarSeGuardaLaTasaVigenteYElDesgloseDependeDeQuienMira() {
        Session op = operator();
        String before = as(op).get("/api/v1/operator/parameters").then().extract()
                .path("data.find { it.key == 'commission_rate' }.value");
        var a = Flows.awarded("Comision");                       // con la tasa vigente (8 % por omisión)
        var pymeView = as(a.pyme()).get("/api/v1/shipments/" + a.shipmentId()).then().statusCode(200)
                .body("data.priceBreakdown.vatRate", num(0.18))
                .body("data.priceBreakdown.commission", nullValue())
                .body("data.priceBreakdown.carrierNet", nullValue())
                .body("data.priceBreakdown.commissionRate", nullValue()).extract();
        BigDecimal amount = d(pymeView.path("data.priceBreakdown.amount"));
        BigDecimal net = d(pymeView.path("data.priceBreakdown.netOfVat"));
        BigDecimal vat = d(pymeView.path("data.priceBreakdown.vat"));
        assertEquals(0, amount.compareTo(d(pymeView.path("data.agreedPrice"))));
        assertEquals(0, amount.compareTo(net.add(vat)), "valor de venta + IGV = precio");
        assertEquals(0, net.compareTo(amount.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP)));

        BigDecimal rate = new BigDecimal(before);
        var carrierView = as(a.carrier()).get("/api/v1/shipments/" + a.shipmentId()).then().statusCode(200).extract();
        BigDecimal commission = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commissionVat = commission.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, rate.compareTo(d(carrierView.path("data.priceBreakdown.commissionRate"))));
        assertEquals(0, commission.compareTo(d(carrierView.path("data.priceBreakdown.commission"))));
        assertEquals(0, commissionVat.compareTo(d(carrierView.path("data.priceBreakdown.commissionVat"))));
        assertEquals(0, amount.subtract(commission).subtract(commissionVat)
                .compareTo(d(carrierView.path("data.priceBreakdown.carrierNet"))));

        // El operador cambia la tasa: el envío ya creado conserva la guardada
        String target = before.equals("0.1") ? "0.12" : "0.1";
        try {
            as(op).body(Map.of("value", target, "reason", "Prueba de tasa guardada")).put("/api/v1/operator/parameters/commission_rate")
                    .then().statusCode(200);
            as(a.carrier()).get("/api/v1/shipments/" + a.shipmentId()).then()
                    .body("data.priceBreakdown.commissionRate", num(rate.doubleValue()))
                    .body("data.priceBreakdown.commission", num(commission.doubleValue()));
            // y un envío nuevo toma la tasa nueva
            var b = Flows.awarded("Comision2");
            as(b.carrier()).get("/api/v1/shipments/" + b.shipmentId()).then()
                    .body("data.priceBreakdown.commissionRate", num(Double.parseDouble(target)));
            // El conductor no ve la comisión
            as(a.driver()).get("/api/v1/shipments/" + a.shipmentId()).then().statusCode(200)
                    .body("data.priceBreakdown.commission", nullValue()).body("data.priceBreakdown.vat", notNullValue());
        } finally {
            as(op).body(Map.of("value", before, "reason", "Restaurar la tasa de la prueba"))
                    .put("/api/v1/operator/parameters/commission_rate").then().statusCode(200);
        }
    }

    @Test
    void elDesgloseDelEjemploDelDuenoSeObtienePorLaApi() {
        Session carrier = register("CARRIER", "Neto Ejemplo");
        var op = operator();
        String before = as(op).get("/api/v1/operator/parameters").then().extract()
                .path("data.find { it.key == 'commission_rate' }.value");
        try {
            as(op).body(Map.of("value", "0.08", "reason", "Ejemplo del dueño: 1,180 al 8 %"))
                    .put("/api/v1/operator/parameters/commission_rate").then().statusCode(200);
            as(carrier).get("/api/v1/pricing/commission?amount=1180").then().statusCode(200)
                    .body("data.breakdown.netOfVat", num(1000)).body("data.breakdown.vat", num(180))
                    .body("data.breakdown.commission", num(80)).body("data.breakdown.commissionVat", num(14.40))
                    .body("data.breakdown.carrierNet", num(1085.60));
        } finally {
            as(op).body(Map.of("value", before, "reason", "Restaurar la tasa de la prueba"))
                    .put("/api/v1/operator/parameters/commission_rate").then().statusCode(200);
        }
    }
}
