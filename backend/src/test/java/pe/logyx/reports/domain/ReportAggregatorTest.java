package pe.logyx.reports.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReportAggregatorTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static ReportLine line(String month, UUID carrier, String route, String finalPrice, String suggested,
                                   String agreed, String commission, String kg, String capacity) {
        BigDecimal fin = new BigDecimal(finalPrice);
        BigDecimal com = new BigDecimal(commission);
        BigDecimal comVat = com.multiply(new BigDecimal("0.18")).setScale(2, java.math.RoundingMode.HALF_UP);
        String[] r = route.split("-");
        return new ReportLine(UUID.randomUUID(), "OC-X", LocalDate.parse(month + "-10"), null, "DELIVERED",
                UUID.randomUUID(), "P", carrier, "C", r[0], r[1], "GENERAL", new BigDecimal(kg), new BigDecimal("100"),
                new BigDecimal(suggested), new BigDecimal(agreed), fin.subtract(new BigDecimal(agreed)), fin,
                fin.divide(new BigDecimal("1.18"), 2, java.math.RoundingMode.HALF_UP), BigDecimal.ZERO,
                new BigDecimal("0.08"), com, comVat, fin.subtract(com).subtract(comVat), null, null,
                capacity == null ? null : new BigDecimal(capacity), "CONFIRMED");
    }

    @Test
    void sumaTotalesAhorroCostoPorKgYOcupacion() {
        var lines = List.of(
                line("2026-10", A, "LIMA-JULIACA", "1180", "1300", "1180", "80", "2000", "8000"),
                line("2026-11", A, "LIMA-JULIACA", "1298", "1200", "1180", "88", "6000", "8000"),
                line("2026-11", B, "LIMA-PUNO", "590", "600", "590", "40", "1000", null));
        var t = ReportAggregator.totals(lines);
        assertEquals(3, t.shipments());
        assertEquals(0, new BigDecimal("3068").compareTo(t.finalTotal()));
        assertEquals(0, new BigDecimal("150").compareTo(t.savings()), "120 − 0 + 10 + 20");
        assertEquals(0, new BigDecimal("118").compareTo(t.adjustmentsTotal()));
        assertEquals(0, new BigDecimal("0.5000").compareTo(t.occupancy()), "(0.25 + 0.75) / 2; sin capacidad no cuenta");
        assertEquals(0, new BigDecimal("0.3409").compareTo(t.costPerKg()));
        assertEquals(0, new BigDecimal("0.0484").compareTo(t.savingsRate()));

        var byMonth = ReportAggregator.groupBy(lines, ReportAggregator::month, true);
        assertEquals(List.of("2026-10", "2026-11"), byMonth.stream().map(ReportAggregator.Group::key).toList());
        var byRoute = ReportAggregator.groupBy(lines, ReportLine::route, false);
        assertEquals("LIMA-JULIACA", byRoute.get(0).key(), "el de mayor monto primero");
        assertEquals(0, new BigDecimal("168").compareTo(byRoute.get(0).totals().commission()));
    }

    @Test
    void sinEnviosTodoEnCero() {
        var t = ReportAggregator.totals(List.of());
        assertEquals(0, t.shipments());
        assertEquals(BigDecimal.ZERO, t.savingsRate());
        assertNull(t.occupancy());
    }
}
