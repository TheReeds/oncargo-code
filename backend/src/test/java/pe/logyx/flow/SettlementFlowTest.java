package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.settlement.application.SettlementReminders;
import pe.logyx.settlement.application.SettlementService;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Cobros sin pasarela (2.12, V24, ADR-0014): comprobante del transportista, pago de la PYME, confirmación, disputa,
 * comisión cobrada por el operador, bloqueo de ajustes y recordatorios. Los montos usan el precio final (acordado +
 * ajustes aceptados) con la comisión sobre el valor de venta sin IGV y su propio IGV.
 */
@QuarkusTest
class SettlementFlowTest {

    @Inject AuthService auth;
    @Inject SystemTx systemTx;
    @Inject EntityManager em;
    @Inject SettlementReminders reminders;

    private Session operator() {
        String email = "opcobros" + System.nanoTime() + "@logyx.pe";
        var r = auth.provisionOperator("LOGYX Cobros " + System.nanoTime() % 1000, Api.newRuc(), "Operador Cobros",
                email, "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), email);
    }

    private static String base(String shipmentId) {
        return "/api/v1/shipments/" + shipmentId + "/settlement";
    }

    private static BigDecimal d(Object v) {
        return new BigDecimal(String.valueOf(v));
    }

    private static String today() {
        return SettlementService.today().toString();
    }

    @Test
    void cobroCompletoConPrecioFinalIgvYComisionSobreElValorDeVenta() {
        Flows.Awarded a = Flows.delivered("Cobro completo");
        String id = a.shipmentId();
        // Un peaje aceptado entra al precio final (ADR-0012)
        String toll = as(a.carrier()).body(Map.of("type", "TOLL", "amount", 118)).post("/api/v1/shipments/" + id
                + "/adjustments").then().statusCode(201).extract().path("data.id");
        as(a.pyme()).body(Map.of()).post("/api/v1/adjustments/" + toll + "/accept").then().statusCode(200);

        var pymeView = as(a.pyme()).get(base(id)).then().statusCode(200)
                .body("data.status", is("PENDING_INVOICE"))
                .body("data.commission", nullValue()).body("data.carrierNet", nullValue())
                .body("data.commissionStatus", nullValue())
                .body("data.actions", contains("PAY")).extract();
        BigDecimal agreed = d(pymeView.path("data.agreedPrice"));
        BigDecimal finalPrice = d(pymeView.path("data.finalPrice"));
        assertEquals(0, agreed.add(new BigDecimal("118")).compareTo(finalPrice), "precio final = acordado + ajuste");
        BigDecimal net = finalPrice.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        assertEquals(0, net.compareTo(d(pymeView.path("data.netOfVat"))));
        assertEquals(0, finalPrice.subtract(net).compareTo(d(pymeView.path("data.vat"))));

        var carrierView = as(a.carrier()).get(base(id)).then().statusCode(200)
                .body("data.actions", contains("INVOICE")).body("data.commissionStatus", is("PENDING")).extract();
        BigDecimal rate = d(carrierView.path("data.commissionRate"));
        BigDecimal commission = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commissionVat = commission.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, commission.compareTo(d(carrierView.path("data.commission"))));
        assertEquals(0, commissionVat.compareTo(d(carrierView.path("data.commissionVat"))));
        assertEquals(0, finalPrice.subtract(commission).subtract(commissionVat)
                .compareTo(d(carrierView.path("data.carrierNet"))));

        // Con un ajuste pendiente no se factura (el monto quedaría abierto)
        String pending = as(a.carrier()).body(Map.of("type", "HANDLING", "amount", 50)).post("/api/v1/shipments/" + id
                + "/adjustments").then().statusCode(201).extract().path("data.id");
        as(a.carrier()).body(Map.of("invoiceNumber", "F001-000123", "issuedOn", today())).post(base(id) + "/invoice")
                .then().statusCode(409).body("error.code", is("LOGYX-STL-003"));
        as(a.pyme()).body(Map.of("note", "No corresponde")).post("/api/v1/adjustments/" + pending + "/reject")
                .then().statusCode(200);

        // El comprobante lo sube y registra el transportista; la PYME no
        upload(a.pyme(), id, "CARRIER_INVOICE", null, "f.pdf", PDF, "application/pdf").statusCode(403)
                .body("error.code", is("LOGYX-DOC-010"));
        String invoiceDoc = upload(a.carrier(), id, "CARRIER_INVOICE", null, "f.pdf", PDF, "application/pdf")
                .statusCode(201).extract().path("data.id");
        as(a.pyme()).body(Map.of("invoiceNumber", "F001-1", "issuedOn", today())).post(base(id) + "/invoice")
                .then().statusCode(403);
        as(a.driver()).body(Map.of("invoiceNumber", "F001-1", "issuedOn", today())).post(base(id) + "/invoice")
                .then().statusCode(403);
        as(a.carrier()).body(Map.of("invoiceNumber", "F001-000123", "issuedOn", today(), "documentId", invoiceDoc))
                .post(base(id) + "/invoice").then().statusCode(200)
                .body("data.status", is("INVOICED")).body("data.invoice.number", is("F001-000123"))
                .body("data.adjustmentsLocked", is(true));

        // Facturado: el precio final ya no cambia
        as(a.carrier()).body(Map.of("type", "TOLL", "amount", 20)).post("/api/v1/shipments/" + id + "/adjustments")
                .then().statusCode(409).body("error.code", is("LOGYX-ADJ-009"));
        as(a.driver()).body(Map.of("type", "TOLL", "amount", 20)).post("/api/v1/shipments/" + id + "/adjustments")
                .then().statusCode(409).body("error.code", is("LOGYX-ADJ-009"));

        // La PYME paga con Yape y adjunta la constancia
        String proof = upload(a.pyme(), id, "PAYMENT_PROOF", null, "yape.png", PNG, "image/png").statusCode(201)
                .extract().path("data.id");
        as(a.pyme()).body(Map.of("method", "YAPE", "paidOn", today(), "amount", finalPrice))
                .post(base(id) + "/payment").then().statusCode(400).body("error.field", is("operationNumber"));
        as(a.pyme()).body(Map.of("method", "YAPE", "operationNumber", "00981234", "paidOn", today(),
                        "amount", finalPrice, "documentId", invoiceDoc))
                .post(base(id) + "/payment").then().statusCode(400).body("error.code", is("LOGYX-STL-009"));
        as(a.pyme()).body(Map.of("method", "YAPE", "operationNumber", "00981234", "paidOn", today(),
                        "amount", finalPrice, "documentId", proof))
                .post(base(id) + "/payment").then().statusCode(200)
                .body("data.status", is("PAID")).body("data.payment.method", is("YAPE"))
                .body("data.payment.operationNumber", is("00981234")).body("data.actions", contains("DISPUTE"));
        as(a.carrier()).body(Map.of("method", "CASH", "paidOn", today(), "amount", 1)).post(base(id) + "/payment")
                .then().statusCode(403);

        as(a.carrier()).post(base(id) + "/confirm").then().statusCode(200)
                .body("data.status", is("CONFIRMED")).body("data.history.to", contains("INVOICED", "PAID", "CONFIRMED"))
                .body("data.history.actorSide", contains("CARRIER", "REQUESTER", "CARRIER"));
        as(a.carrier()).post(base(id) + "/confirm").then().statusCode(409).body("error.code", is("LOGYX-STL-002"));

        // Nadie ajeno lo ve; el conductor tampoco
        Session other = register("PYME", "Cobros ajena");
        as(other).get(base(id)).then().statusCode(404);
        as(a.driver()).get(base(id)).then().statusCode(403);
        as(other).get("/api/v1/settlements").then().statusCode(200).body("data.shipmentId", not(hasItem(id)));
        as(a.pyme()).get("/api/v1/settlements?status=CONFIRMED").then().statusCode(200)
                .body("data.find { it.shipmentId == '" + id + "' }.commission", nullValue())
                .body("data.find { it.shipmentId == '" + id + "' }.finalPrice", notNullValue());

        // El operador cobra la comisión (con su IGV): el monto se calcula, no se escribe
        Session op = operator();
        as(a.pyme()).get("/api/v1/settlements/commission-collections").then().statusCode(403);
        var collection = as(op).body(Map.of("carrierOrgId", a.carrier().orgId(), "shipmentIds", List.of(id),
                        "method", "TRANSFER", "operationNumber", "OP-778899", "paidOn", today()))
                .post("/api/v1/settlements/commission-collections").then().statusCode(200)
                .body("data.shipmentsCount", is(1)).extract();
        assertEquals(0, commission.add(commissionVat).compareTo(d(collection.path("data.amount"))));
        as(op).body(Map.of("carrierOrgId", a.carrier().orgId(), "shipmentIds", List.of(id),
                        "method", "TRANSFER", "operationNumber", "OP-2", "paidOn", today()))
                .post("/api/v1/settlements/commission-collections").then().statusCode(409)
                .body("error.code", is("LOGYX-STL-006"));
        as(op).body(Map.of("carrierOrgId", other.orgId(), "shipmentIds", List.of(id),
                        "method", "TRANSFER", "operationNumber", "OP-3", "paidOn", today()))
                .post("/api/v1/settlements/commission-collections").then().statusCode(400)
                .body("error.code", is("LOGYX-STL-010"));
        as(a.carrier()).get("/api/v1/settlements/commission-collections").then().statusCode(200)
                .body("data.id", hasItem(collection.path("data.id").toString()));
        as(a.carrier()).get(base(id)).then().body("data.commissionStatus", is("COLLECTED"));
        as(op).get("/api/v1/settlements?carrierOrgId=" + a.carrier().orgId()).then().statusCode(200)
                .body("data.find { it.shipmentId == '" + id + "' }.commissionStatus", is("COLLECTED"));
    }

    @Test
    void laDisputaLaRetiraQuienLaAbrioOLaResuelveElOperador() {
        Flows.Awarded a = Flows.delivered("Cobro disputa");
        String id = a.shipmentId();
        as(a.pyme()).body(Map.of("reason", "No llegó la factura")).post(base(id) + "/dispute").then().statusCode(409)
                .body("error.code", is("LOGYX-STL-004"));
        as(a.carrier()).body(Map.of("invoiceNumber", "E001-55", "issuedOn", today())).post(base(id) + "/invoice")
                .then().statusCode(200);
        as(a.pyme()).body(Map.of("reason", "El monto no coincide con lo pactado")).post(base(id) + "/dispute")
                .then().statusCode(200).body("data.status", is("DISPUTED"))
                .body("data.dispute.openedBy", is("REQUESTER")).body("data.actions", contains("WITHDRAW_DISPUTE"));
        as(a.carrier()).post(base(id) + "/confirm").then().statusCode(409);
        as(a.carrier()).body(Map.of()).post(base(id) + "/resolve").then().statusCode(403)
                .body("error.code", is("LOGYX-STL-005"));
        as(a.pyme()).body(Map.of("status", "PENDING_INVOICE")).post(base(id) + "/resolve").then().statusCode(403);
        as(a.pyme()).body(Map.of()).post(base(id) + "/resolve").then().statusCode(200)
                .body("data.status", is("INVOICED")).body("data.dispute", nullValue());

        as(a.carrier()).body(Map.of("reason", "Necesito corregir el comprobante")).post(base(id) + "/dispute")
                .then().statusCode(200);
        Session op = operator();
        as(op).get(base(id)).then().statusCode(200).body("data.actions", contains("RESOLVE"));
        as(op).body(Map.of("status", "PENDING_INVOICE")).post(base(id) + "/resolve").then().statusCode(400)
                .body("error.field", is("note"));
        as(op).body(Map.of("status", "PENDING_INVOICE", "note", "Se anula la factura E001-55"))
                .post(base(id) + "/resolve").then().statusCode(200)
                .body("data.status", is("PENDING_INVOICE")).body("data.invoice", nullValue())
                .body("data.adjustmentsLocked", is(false));
        // Volver a «falta comprobante» reabre los ajustes
        as(a.carrier()).body(Map.of("type", "TOLL", "amount", 15)).post("/api/v1/shipments/" + id + "/adjustments")
                .then().statusCode(201);
    }

    @Test
    void soloHayCobroDeEnviosEntregados() {
        Flows.Awarded a = Flows.awarded("Cobro pendiente");
        as(a.pyme()).get(base(a.shipmentId())).then().statusCode(409).body("error.code", is("LOGYX-STL-001"));
        as(a.carrier()).body(Map.of("invoiceNumber", "F1", "issuedOn", today())).post(base(a.shipmentId()) + "/invoice")
                .then().statusCode(409).body("error.code", is("LOGYX-STL-001"));
    }

    @Test
    void losRecordatoriosDePagoSalenUnaVezPorTipo() {
        Flows.Awarded a = Flows.delivered("Cobro recordatorio");
        String id = a.shipmentId();
        as(a.pyme()).get(base(id)).then().statusCode(200);                     // abre el cobro
        LocalDate today = SettlementService.today();
        systemTx.run(() -> em.createNativeQuery("UPDATE shipment_settlements SET due_on = ?2 WHERE shipment_id = ?1")
                .setParameter(1, java.util.UUID.fromString(id)).setParameter(2, today.plusDays(2)).executeUpdate());
        reminders.sweep(today);
        reminders.sweep(today);
        long soon = systemTx.call(() -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM settlement_notices WHERE shipment_id = ?1 AND kind = 'DUE_SOON'")
                .setParameter(1, java.util.UUID.fromString(id)).getSingleResult()).longValue());
        assertEquals(1L, soon);
        long events = systemTx.call(() -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM outbox_events WHERE event_type LIKE '%PaymentDue' AND payload::text LIKE ?1")
                .setParameter(1, "%" + id + "%").getSingleResult()).longValue());
        assertEquals(1L, events, "un solo evento PaymentDue");

        // Vencido y pagado: ya no recuerda
        systemTx.run(() -> em.createNativeQuery("UPDATE shipment_settlements SET due_on = ?2 WHERE shipment_id = ?1")
                .setParameter(1, java.util.UUID.fromString(id)).setParameter(2, today.minusDays(1)).executeUpdate());
        as(a.pyme()).get(base(id)).then().body("data.daysOverdue", is(1));
        as(a.pyme()).get("/api/v1/settlements?overdue=true").then().body("data.shipmentId", hasItem(id));
        as(a.pyme()).get("/api/v1/settlements/summary").then().statusCode(200)
                .body("data.overdue", greaterThanOrEqualTo(1)).body("data.commissionPending", is(0));
        as(a.pyme()).body(Map.of("method", "TRANSFER", "operationNumber", "123456", "paidOn", today.toString(),
                "amount", 100)).post(base(id) + "/payment").then().statusCode(200);
        reminders.sweep(today);
        long overdue = systemTx.call(() -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM settlement_notices WHERE shipment_id = ?1 AND kind = 'OVERDUE'")
                .setParameter(1, java.util.UUID.fromString(id)).getSingleResult()).longValue());
        assertEquals(0L, overdue);
    }

    @Test
    void losDatosDeCobroLosGestionaElAdminYLosVeLaPymeDelEnvio() {
        Flows.Awarded a = Flows.delivered("Cobro cuentas");
        as(a.carrier()).body(Map.of("kind", "BANK", "bankName", "BCP", "holderName", "Transportes Cobro SAC",
                        "number", "0021 9300 1234 5678 90")).post("/api/v1/organizations/me/payout-accounts")
                .then().statusCode(400).body("error.code", is("LOGYX-STL-007"));
        as(a.carrier()).body(Map.of("kind", "BANK", "bankName", "BCP", "holderName", "Transportes Cobro SAC",
                        "number", "0021-9300-1234-5678-9012")).post("/api/v1/organizations/me/payout-accounts")
                .then().statusCode(200).body("data.primary", is(true)).body("data.last4", is("9012"))
                .body("data.number", is("00219300123456789012"));
        as(a.carrier()).body(Map.of("kind", "YAPE", "holderName", "Transportes Cobro SAC", "number", "+51 987 654 321"))
                .post("/api/v1/organizations/me/payout-accounts").then().statusCode(200)
                .body("data.primary", is(false)).body("data.number", is("987654321"));
        as(a.pyme()).get("/api/v1/organizations/me/payout-accounts").then().statusCode(403);
        as(a.pyme()).get(base(a.shipmentId())).then().statusCode(200)
                .body("data.payoutAccounts.number", hasItems("00219300123456789012", "987654321"))
                .body("data.payoutAccounts[0].primary", is(true));
        // En la BD el número no está en claro
        long plain = systemTx.call(() -> ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM carrier_payout_accounts WHERE carrier_org_id = ?1 "
                                + "AND encode(number_enc, 'escape') LIKE '%00219300123456789012%'")
                .setParameter(1, java.util.UUID.fromString(a.carrier().orgId())).getSingleResult()).longValue());
        assertEquals(0L, plain);
    }
}
