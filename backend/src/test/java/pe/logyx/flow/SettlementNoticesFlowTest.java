package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.logyx.settlement.application.event.CommissionCollected;
import pe.logyx.settlement.application.event.PaymentDue;
import pe.logyx.settlement.application.event.SettlementUpdated;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Avisos de cobros (V24, 2.12; categoría «Cobros y pagos»): cada paso del cobro llega a la contraparte de quien actuó
 * (a las dos si fue el operador), el recordatorio de pago solo a la PYME y el cobro de la comisión solo al
 * transportista. Los eventos se disparan con datos de prueba, como los publicaría {@code settlement}.
 */
@QuarkusTest
class SettlementNoticesFlowTest {

    @Inject SystemTx systemTx;
    @Inject Event<SettlementUpdated> updated;
    @Inject Event<PaymentDue> due;
    @Inject Event<CommissionCollected> commission;

    private record Parties(Session pyme, Session carrier, UUID shipmentId) {
        UUID pymeOrg() { return UUID.fromString(pyme.orgId()); }
        UUID carrierOrg() { return UUID.fromString(carrier.orgId()); }
    }

    private static Parties parties(String name) {
        return new Parties(register("PYME", name + " Pyme"), register("CARRIER", name + " Carrier"), UUID.randomUUID());
    }

    private void step(Parties p, String from, String to, UUID actor, boolean byOperator, String note) {
        var e = new SettlementUpdated(p.shipmentId(), p.pymeOrg(), p.carrierOrg(), "OC-COB01", "Lima → Juliaca", from, to,
                actor, byOperator, new BigDecimal("1180.00"), note);
        systemTx.run(() -> updated.fire(e));
    }

    private static List<Map<String, Object>> notices(Session s) {
        return as(s).get("/api/v1/notifications?size=50").then().statusCode(200).extract().path("data");
    }

    @Test
    void cadaPasoDelCobroLlegaALaContraparte() {
        Parties p = parties("Cobro Pasos");
        step(p, "PENDING_INVOICE", "INVOICED", p.carrierOrg(), false, null);
        assertEquals(0, notices(p.carrier()).size(), "quien emite no se avisa a sí mismo");
        as(p.pyme()).get("/api/v1/notifications").then().statusCode(200)
                .body("data[0].type", is("SETTLEMENT_UPDATED"), "data[0].relatedType", is("SHIPMENT"),
                        "data[0].relatedId", is(p.shipmentId().toString()),
                        "data[0].title", is("Comprobante emitido · OC-COB01"),
                        "data[0].body", containsString("S/ 1,180.00"));

        step(p, "INVOICED", "PAID", p.pymeOrg(), false, null);
        as(p.carrier()).get("/api/v1/notifications").then()
                .body("data[0].title", is("Pago registrado · OC-COB01"), "data[0].body", containsString("La PYME registró el pago de S/ 1,180.00"));
        assertEquals(1, notices(p.pyme()).size());

        step(p, "PAID", "DISPUTED", p.carrierOrg(), false, "No veo el abono en mi cuenta");
        as(p.pyme()).get("/api/v1/notifications").then()
                .body("data[0].title", is("Cobro en disputa · OC-COB01"), "data[0].body", containsString("No veo el abono"));

        // El operador resuelve: a las dos partes
        step(p, "DISPUTED", "PAID", UUID.randomUUID(), true, "Abono verificado");
        as(p.pyme()).get("/api/v1/notifications").then().body("data[0].title", is("Disputa resuelta · OC-COB01"),
                "data[0].body", containsString("«Pagado»"));
        as(p.carrier()).get("/api/v1/notifications").then().body("data[0].title", is("Disputa resuelta · OC-COB01"));

        step(p, "PAID", "CONFIRMED", p.carrierOrg(), false, null);
        as(p.pyme()).get("/api/v1/notifications").then().body("data[0].title", is("Pago confirmado · OC-COB01"));
        // Ningún aviso de cobro menciona la comisión
        notices(p.pyme()).forEach(n -> assertFalse(((String) n.get("body")).toLowerCase().contains("comisión")));
        as(p.pyme()).get("/api/v1/notifications/preferences").then()
                .body("data.find { it.category == 'PAYMENTS' }.email", is(true))
                .body("data.find { it.category == 'PAYMENTS' }.label", is("Cobros y pagos"));
    }

    @Test
    void elRecordatorioEsDeLaPymeYLaComisionSoloDelTransportista() {
        Parties p = parties("Cobro Avisos");
        var soon = new PaymentDue(p.shipmentId(), p.pymeOrg(), p.carrierOrg(), "OC-COB02", "Arequipa → Puno", "DUE_SOON",
                LocalDate.of(2026, 12, 3), 0, new BigDecimal("950.00"));
        var late = new PaymentDue(p.shipmentId(), p.pymeOrg(), p.carrierOrg(), "OC-COB02", "Arequipa → Puno", "OVERDUE",
                LocalDate.of(2026, 12, 3), 4, new BigDecimal("950.00"));
        systemTx.run(() -> { due.fire(soon); due.fire(late); });
        var mine = notices(p.pyme());
        assertEquals(2, mine.size());
        assertTrue(mine.stream().anyMatch(n -> "Pago por vencer · OC-COB02".equals(n.get("title"))
                && ((String) n.get("body")).contains("vence el 3/12/2026")));
        assertTrue(mine.stream().anyMatch(n -> "Pago vencido · OC-COB02".equals(n.get("title"))
                && ((String) n.get("body")).contains("hace 4 días")));
        assertTrue(mine.stream().allMatch(n -> "PAYMENT_DUE".equals(n.get("type"))));
        assertEquals(0, notices(p.carrier()).size());

        var c = new CommissionCollected(UUID.randomUUID(), p.carrierOrg(), new BigDecimal("94.40"), 1, "OP-7781");
        systemTx.run(() -> commission.fire(c));
        as(p.carrier()).get("/api/v1/notifications").then().body("data[0].type", is("COMMISSION_COLLECTED"),
                "data[0].body", containsString("S/ 94.40"), "data[0].body", containsString("1 envío"),
                "data[0].body", containsString("OP-7781"), "data[0].relatedType", nullValue());
        assertEquals(2, notices(p.pyme()).size(), "la PYME nunca recibe la comisión");
    }
}
