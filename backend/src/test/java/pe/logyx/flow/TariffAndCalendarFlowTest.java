package pe.logyx.flow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.logyx.catalog.application.CalendarService;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.AuthService;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.support.Api;
import pe.logyx.support.Api.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.support.Api.*;

/**
 * Tarifario versionado, parámetros de la subasta y del piso, y calendario de feriados (2.14, V24). Cada test deja los
 * valores como estaban (una versión nueva con los valores anteriores; los parámetros vuelven a su valor).
 */
@QuarkusTest
class TariffAndCalendarFlowTest {

    @Inject AuthService auth;
    @Inject CalendarService calendar;
    @Inject SystemTx systemTx;
    @Inject EntityManager em;

    private Session operator() {
        var r = auth.provisionOperator("LOGYX Tarifas " + System.nanoTime() % 1000, Api.newRuc(), "Operador Tarifas",
                "optar" + System.nanoTime() + "@logyx.pe", "Operador123");
        return new Session(r.accessToken(), r.user().organization().id().toString(), null);
    }

    private static Map<String, Object> quoteBody() {
        return Map.of("originCity", "LIMA", "destinationCity", "AREQUIPA", "cargoType", "GENERAL", "weightKg", 2000);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Map<String, Object> values, Object effectiveFrom, String reason) {
        Map<String, Object> b = new HashMap<>(values);
        if (effectiveFrom != null) b.put("effectiveFrom", effectiveFrom);
        b.put("reason", reason);
        return b;
    }

    @Test
    void elOperadorVersionaElTarifarioYAlimentaLaCotizacion() {
        Session op = operator();
        Session pyme = register("PYME", "Tarifas Pyme");
        Map<String, Object> current = as(pyme).get("/api/v1/pricing/tariffs/current").then().statusCode(200)
                .extract().path("data.values");
        float before = as(pyme).body(quoteBody()).post("/api/v1/pricing/calculate").then().statusCode(200)
                .extract().path("data.suggestedPrice");
        as(pyme).get("/api/v1/pricing/tariffs").then().statusCode(403);
        as(pyme).body(body(current, null, "intento")).post("/api/v1/pricing/tariffs").then().statusCode(403);

        Map<String, Object> withBase = new HashMap<>(current);
        withBase.put("baseFee", new BigDecimal(String.valueOf(current.get("baseFee"))).add(new BigDecimal("100")));
        try {
            var created = as(op).body(body(withBase, null, "Tarifa base por gastos administrativos"))
                    .post("/api/v1/pricing/tariffs").then().statusCode(200).body("data.state", is("CURRENT")).extract();
            int version = created.path("data.version");
            var quote = as(pyme).body(quoteBody()).post("/api/v1/pricing/calculate").then().statusCode(200)
                    .body("data.tariffVersion", is(version)).extract();
            assertEquals(before + 100f, (float) quote.path("data.suggestedPrice"), 0.001f);

            // La solicitud guarda la versión con la que se cotizó
            String requestId = publish(pyme, 2000).statusCode(201).extract().path("data.id");
            Object stored = systemTx.call(() -> em.createNativeQuery(
                    "SELECT tariff_version FROM shipment_requests WHERE id = ?1")
                    .setParameter(1, java.util.UUID.fromString(requestId)).getSingleResult());
            assertEquals(version, ((Number) stored).intValue());

            // Una versión programada no rige hasta su fecha y se puede anular
            Map<String, Object> future = new HashMap<>(current);
            future.put("fuelPerKm", new BigDecimal("0.99"));
            String from = OffsetDateTime.now(ZoneId.of("America/Lima")).plusDays(10).toString();
            int scheduled = as(op).body(body(future, from, "Alza del diésel programada"))
                    .post("/api/v1/pricing/tariffs").then().statusCode(200).body("data.state", is("SCHEDULED"))
                    .extract().path("data.version");
            as(pyme).body(quoteBody()).post("/api/v1/pricing/calculate").then().body("data.tariffVersion", is(version));
            as(op).body(Map.of("reason", "Se posterga")).post("/api/v1/pricing/tariffs/" + scheduled + "/cancel")
                    .then().statusCode(200).body("data.state", is("CANCELLED"));
            as(op).body(Map.of("reason", "Otra vez")).post("/api/v1/pricing/tariffs/" + scheduled + "/cancel")
                    .then().statusCode(409).body("error.code", is("LOGYX-TRF-004"));
            as(op).body(Map.of("reason", "Ya rige")).post("/api/v1/pricing/tariffs/" + version + "/cancel")
                    .then().statusCode(409);
            as(op).get("/api/v1/pricing/tariffs").then().statusCode(200)
                    .body("data.find { it.version == " + scheduled + " }.state", is("CANCELLED"))
                    .body("data.find { it.version == " + version + " }.state", is("CURRENT"));

            // Validaciones
            Map<String, Object> bad = new HashMap<>(current);
            Map<String, Object> mult = new HashMap<>((Map<String, Object>) current.get("cargoMultipliers"));
            mult.put("FRAGILE", 9);
            bad.put("cargoMultipliers", mult);
            as(op).body(body(bad, null, "Multiplicador alto")).post("/api/v1/pricing/tariffs").then().statusCode(400)
                    .body("error.code", is("LOGYX-TRF-001")).body("error.field", is("cargoMultipliers.FRAGILE"));
            as(op).body(body(current, "2020-01-01T00:00:00-05:00", "Retroactiva")).post("/api/v1/pricing/tariffs")
                    .then().statusCode(400).body("error.code", is("LOGYX-TRF-003"));
            Map<String, Object> stale = body(current, null, "Con versión vieja");
            stale.put("expectedLatestVersion", 1);
            as(op).body(stale).post("/api/v1/pricing/tariffs").then().statusCode(409).body("error.code", is("LOGYX-TRF-005"));
        } finally {
            as(op).body(body(current, null, "Restaura la tarifa del test")).post("/api/v1/pricing/tariffs")
                    .then().statusCode(200);
        }
        float after = as(pyme).body(quoteBody()).post("/api/v1/pricing/calculate").then().extract().path("data.suggestedPrice");
        assertEquals(before, after, 0.001f);
    }

    @Test
    void laSubastaYElPisoSonParametrosDelOperador() {
        Session op = operator();
        Session pyme = register("PYME", "Subasta Pyme");
        Map<String, String> original = new HashMap<>();
        for (String k : new String[]{"auction_duration_minutes", "auction_max_bids", "price_floor_ratio"}) {
            original.put(k, as(op).get("/api/v1/operator/parameters").then().statusCode(200).extract()
                    .path("data.find { it.key == '" + k + "' }.value"));
        }
        assertEquals("MINUTES", as(op).get("/api/v1/operator/parameters").then().extract()
                .path("data.find { it.key == 'auction_duration_minutes' }.kind"));
        try {
            as(op).body(Map.of("value", "90", "reason", "Subastas de hora y media")).put("/api/v1/operator/parameters/auction_duration_minutes")
                    .then().statusCode(200);
            as(op).body(Map.of("value", "2", "reason", "Cierra con dos ofertas")).put("/api/v1/operator/parameters/auction_max_bids")
                    .then().statusCode(200);
            as(op).body(Map.of("value", "0.8", "reason", "Piso más alto")).put("/api/v1/operator/parameters/price_floor_ratio")
                    .then().statusCode(200);
            as(op).body(Map.of("value", "1.5", "reason", "Valor no entero")).put("/api/v1/operator/parameters/auction_max_bids")
                    .then().statusCode(400).body("error.code", is("LOGYX-PAR-001"));
            var req = publish(pyme, 2000).statusCode(201).body("data.auction.maxBids", is(2)).extract();
            OffsetDateTime closes = OffsetDateTime.parse(req.path("data.auction.closesAt"));
            long minutes = java.time.Duration.between(OffsetDateTime.now(), closes).toMinutes();
            assertTrue(minutes >= 88 && minutes <= 90, "cierra en ~90 min: " + minutes);
            float suggested = req.path("data.suggestedPrice");
            float floor = req.path("data.priceFloor");
            assertEquals(new BigDecimal(String.valueOf(suggested)).multiply(new BigDecimal("0.8"))
                    .setScale(2, java.math.RoundingMode.HALF_UP).floatValue(), floor, 0.001f);
        } finally {
            original.forEach((k, v) -> as(op).body(Map.of("value", v, "reason", "Restaura el valor del test"))
                    .put("/api/v1/operator/parameters/" + k).then().statusCode(200));
        }
    }

    @Test
    void elCalendarioDeFeriadosYParosLoEditaElOperadorYLoUsanLasPlantillas() {
        Session op = operator();
        Session pyme = register("PYME", "Calendario Pyme");
        as(pyme).get("/api/v1/catalog/calendar?from=2026-12-01&to=2026-12-31").then().statusCode(200)
                .body("data.name", hasItems("Navidad", "Inmaculada Concepción")).body("data.national", everyItem(is(true)));
        LocalDate day = LocalDate.now(ZoneId.of("America/Lima")).plusDays(20);
        while (!calendar.on(day, null).isEmpty() || pe.logyx.catalog.domain.NationalHolidays.isHoliday(day)) {
            day = day.plusDays(1);                                    // un día sin feriado
        }
        String name = "Paro regional " + System.nanoTime() % 100000;
        Map<String, Object> strike = Map.of("day", day.toString(), "kind", "STRIKE", "name", name,
                "cities", java.util.List.of("PUNO", "JULIACA"));
        as(pyme).body(strike).post("/api/v1/catalog/calendar").then().statusCode(403);
        String id = as(op).body(strike).post("/api/v1/catalog/calendar").then().statusCode(200)
                .body("data.national", is(false)).body("data.cities", contains("JULIACA", "PUNO"))
                .body("data.skipRecurrence", is(true)).extract().path("data.id");
        as(op).body(strike).post("/api/v1/catalog/calendar").then().statusCode(409).body("error.code", is("LOGYX-CAL-002"));
        as(op).body(Map.of("day", "2020-01-01", "kind", "HOLIDAY", "name", "Pasado")).post("/api/v1/catalog/calendar")
                .then().statusCode(400).body("error.code", is("LOGYX-CAL-001"));
        String range = "?from=" + day + "&to=" + day;
        as(pyme).get("/api/v1/catalog/calendar" + range + "&city=PUNO").then().body("data.name", hasItem(name));
        as(pyme).get("/api/v1/catalog/calendar" + range + "&city=LIMA").then().body("data.name", not(hasItem(name)));
        calendar.clearCache();
        assertTrue(calendar.skipsRecurrence(day, City.PUNO), "la plantilla con origen Puno lo salta");
        assertFalse(calendar.skipsRecurrence(day, City.LIMA));
        assertTrue(calendar.skipsRecurrence(LocalDate.of(2031, 12, 25), City.LIMA), "año sin cargar: lista nacional");
        assertTrue(calendar.skipsRecurrence(LocalDate.of(2026, 12, 25), City.LIMA));

        as(op).delete("/api/v1/catalog/calendar/" + id).then().statusCode(400);
        as(op).delete("/api/v1/catalog/calendar/" + id + "?reason=Se levantó el paro").then().statusCode(200);
        calendar.clearCache();
        assertFalse(calendar.skipsRecurrence(day, City.PUNO));
        long audited = systemTx.call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM audit_log WHERE action IN ('CALENDAR_DAY_ADDED', 'CALENDAR_DAY_REMOVED') "
                        + "AND entity_id = ?1").setParameter(1, java.util.UUID.fromString(id)).getSingleResult()).longValue());
        assertEquals(2L, audited);
    }
}
