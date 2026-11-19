package pe.logyx.security;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.identity.application.AuthService;
import pe.logyx.settlement.application.SettlementService;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;
import pe.logyx.support.Flows;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * RLS de la Ola 2 zona F (V24), en PostgreSQL y con consultas que NO filtran por organización: cobros e historial (solo
 * las partes y el operador; nunca un conductor; la comisión solo la toca el operador), cobros de comisión (el
 * transportista y el operador), datos de cobro (el transportista; la PYME solo si tiene un envío con él), bitácora de
 * recordatorios (solo sistema) y tarifario/calendario (todos leen, solo el operador escribe).
 */
@QuarkusTest
class FinanceRowLevelSecurityTest {

    @Inject EntityManager em;
    @Inject AuthService auth;

    private static String profileOf(Session s) {
        String payload = s.token().split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        var m = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("token sin sub");
        return m.group(1);
    }

    private <T> T as(Session s, String orgType, java.util.function.Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true), "
                            + "set_config('app.profile_id', ?3, true)")
                    .setParameter(1, s.orgId()).setParameter(2, orgType).setParameter(3, profileOf(s)).getSingleResult();
            em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
            return work.get();
        });
    }

    private long countAs(Session s, String orgType, String sql) {
        return as(s, orgType, () -> ((Number) em.createNativeQuery(sql).getSingleResult()).longValue());
    }

    private int executeAs(Session s, String orgType, String sql) {
        return as(s, orgType, () -> em.createNativeQuery(sql).executeUpdate());
    }

    private Session operator() {
        var r = auth.provisionOperator("RLS F " + System.nanoTime() % 1000, Api.newRuc(), "Op F",
                "opf" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    @Test
    void cadaParteVeSoloSusCobrosYLaComisionLaTocaSoloElOperador() {
        Flows.Awarded a = Flows.delivered("RLS Cobros");
        Session otherPyme = register("PYME", "RLS Cobros otra pyme");
        Session otherCarrier = register("CARRIER", "RLS Cobros otro carrier");
        Session op = operator();
        String id = a.shipmentId();
        Api.as(a.carrier()).body(Map.of("invoiceNumber", "F001-9", "issuedOn", SettlementService.today().toString()))
                .post("/api/v1/shipments/" + id + "/settlement/invoice").then().statusCode(200);
        Api.as(op).body(Map.of("carrierOrgId", a.carrier().orgId(), "shipmentIds", List.of(id), "method", "TRANSFER",
                "operationNumber", "OP-RLS", "paidOn", SettlementService.today().toString()))
                .post("/api/v1/settlements/commission-collections").then().statusCode(200);
        Api.as(a.carrier()).body(Map.of("kind", "YAPE", "holderName", "Titular RLS", "number", "987000111"))
                .post("/api/v1/organizations/me/payout-accounts").then().statusCode(200);

        String all = "SELECT count(*) FROM shipment_settlements WHERE shipment_id = '" + id + "'";
        assertEquals(1L, countAs(a.pyme(), "PYME", all));
        assertEquals(1L, countAs(a.carrier(), "CARRIER", all));
        assertEquals(1L, countAs(op, "OPERATOR", all));
        assertEquals(0L, countAs(otherPyme, "PYME", all));
        assertEquals(0L, countAs(otherCarrier, "CARRIER", all));
        assertEquals(0L, countAs(a.driver(), "CARRIER", all), "el conductor no ve cobros");
        String hist = "SELECT count(*) FROM settlement_history WHERE shipment_id = '" + id + "'";
        assertEquals(1L, countAs(a.pyme(), "PYME", hist));
        assertEquals(0L, countAs(otherPyme, "PYME", hist));
        assertEquals(0L, countAs(a.driver(), "CARRIER", hist));

        // Nadie ajeno escribe; una parte no toca la comisión (trigger) ni borra
        assertEquals(0, executeAs(otherPyme, "PYME", "UPDATE shipment_settlements SET status = 'CONFIRMED'"));
        assertThrows(Exception.class, () -> executeAs(a.carrier(), "CARRIER",
                "UPDATE shipment_settlements SET commission_status = 'PENDING', commission_collection_id = NULL "
                        + "WHERE shipment_id = '" + id + "'"));
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME",
                "DELETE FROM shipment_settlements WHERE shipment_id = '" + id + "'"));
        assertThrows(Exception.class, () -> executeAs(a.pyme(), "PYME",
                "UPDATE settlement_history SET note = 'x' WHERE shipment_id = '" + id + "'"));

        // Cobros de comisión: la PYME nunca; el transportista los suyos
        String coll = "SELECT count(*) FROM commission_collections WHERE carrier_org_id = '" + a.carrier().orgId() + "'";
        assertEquals(1L, countAs(a.carrier(), "CARRIER", coll));
        assertEquals(1L, countAs(op, "OPERATOR", coll));
        assertEquals(0L, countAs(a.pyme(), "PYME", coll));
        assertEquals(0L, countAs(otherCarrier, "CARRIER", coll));
        assertThrows(Exception.class, () -> executeAs(a.carrier(), "CARRIER",
                "INSERT INTO commission_collections (id, carrier_org_id, amount, method, operation_number, paid_on, "
                        + "shipments_count, recorded_by, recorded_by_org_id) VALUES (gen_random_uuid(), '"
                        + a.carrier().orgId() + "', 1, 'CASH', 'x', current_date, 1, '" + profileOf(a.carrier())
                        + "', '" + a.carrier().orgId() + "')"));

        // Datos de cobro: la PYME con envío sí, la otra no
        String pay = "SELECT count(*) FROM carrier_payout_accounts WHERE carrier_org_id = '" + a.carrier().orgId() + "'";
        assertEquals(1L, countAs(a.carrier(), "CARRIER", pay));
        assertEquals(1L, countAs(a.pyme(), "PYME", pay));
        assertEquals(0L, countAs(otherPyme, "PYME", pay));
        assertEquals(0L, countAs(otherCarrier, "CARRIER", pay));
        assertEquals(0L, countAs(a.driver(), "CARRIER", pay));
        assertEquals(0, executeAs(a.pyme(), "PYME", "UPDATE carrier_payout_accounts SET holder_name = 'x'"));

        // Bitácora de recordatorios: solo sistema
        assertThrows(Exception.class, () -> countAs(a.pyme(), "PYME", "SELECT count(*) FROM settlement_notices"));
    }

    @Test
    void elTarifarioYElCalendarioLosLeenTodosYLosEscribeSoloElOperador() {
        Session pyme = register("PYME", "RLS Tarifa pyme");
        Session op = operator();
        assertTrue(countAs(pyme, "PYME", "SELECT count(*) FROM tariff_versions") >= 1);
        assertTrue(countAs(pyme, "PYME", "SELECT count(*) FROM calendar_days") >= 1);
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME",
                "INSERT INTO calendar_days (day, kind, name) VALUES (current_date + 400, 'HOLIDAY', 'Falso')"));
        assertThrows(Exception.class, () -> executeAs(pyme, "PYME",
                "INSERT INTO tariff_versions (version, base_fee, fuel_per_km, rate_per_km_ton, toll_every_km, toll_price, "
                        + "handling_rate, cargo_multipliers, effective_from, reason) VALUES (99999, 0, 0, 0, 1, 0, 0, '{}', now(), 'x')"));
        assertEquals(0, executeAs(pyme, "PYME", "DELETE FROM calendar_days"));
        assertThrows(Exception.class, () -> executeAs(op, "OPERATOR", "UPDATE tariff_versions SET base_fee = 1"),
                "ni el operador reescribe una versión: solo la anula");
        String name = "RLS feriado " + System.nanoTime();
        assertEquals(1, executeAs(op, "OPERATOR", "INSERT INTO calendar_days (day, kind, name) VALUES (current_date + 400, "
                + "'OTHER', '" + name + "')"));
        assertEquals(1, executeAs(op, "OPERATOR", "DELETE FROM calendar_days WHERE name = '" + name + "'"));
    }
}
