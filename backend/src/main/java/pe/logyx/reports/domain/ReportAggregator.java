package pe.logyx.reports.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

/**
 * Agrega envíos para los reportes (2.11), en Java puro. Los montos de cada envío ya vienen calculados con la misma
 * aritmética que el envío ({@code CommissionPolicy} sobre el precio final), así que los totales cuadran con la vista
 * del envío, el CSV y la liquidación.
 */
public final class ReportAggregator {

    private ReportAggregator() {}

    /** Totales de un grupo (o de todo). {@code occupancy}: peso / capacidad del vehículo promedio (0–1), si se conoce. */
    public record Totals(long shipments, long delivered, BigDecimal finalTotal, BigDecimal netOfVat, BigDecimal vat,
                         BigDecimal agreedTotal, BigDecimal adjustmentsTotal, BigDecimal suggestedTotal,
                         BigDecimal savings, BigDecimal commission, BigDecimal commissionVat, BigDecimal carrierNet,
                         BigDecimal weightKg, BigDecimal distanceKm, BigDecimal occupancy) {

        /** Ahorro / sugerido (0–1). */
        public BigDecimal savingsRate() {
            return suggestedTotal.signum() == 0 ? BigDecimal.ZERO
                    : savings.divide(suggestedTotal, 4, RoundingMode.HALF_UP);
        }

        /** Costo por kg con IGV. */
        public BigDecimal costPerKg() {
            return weightKg.signum() == 0 ? BigDecimal.ZERO : finalTotal.divide(weightKg, 4, RoundingMode.HALF_UP);
        }

        /** Ingreso neto por km (transportista). */
        public BigDecimal netPerKm() {
            return distanceKm.signum() == 0 ? BigDecimal.ZERO : carrierNet.divide(distanceKm, 2, RoundingMode.HALF_UP);
        }
    }

    public record Group(String key, Totals totals) {}

    public static Totals totals(Collection<ReportLine> lines) {
        long n = 0, delivered = 0, withCapacity = 0;
        BigDecimal fin = BigDecimal.ZERO, net = BigDecimal.ZERO, vat = BigDecimal.ZERO, agreed = BigDecimal.ZERO,
                adj = BigDecimal.ZERO, sug = BigDecimal.ZERO, sav = BigDecimal.ZERO, com = BigDecimal.ZERO,
                comVat = BigDecimal.ZERO, carrierNet = BigDecimal.ZERO, kg = BigDecimal.ZERO, km = BigDecimal.ZERO,
                occ = BigDecimal.ZERO;
        for (ReportLine l : lines) {
            n++;
            if ("DELIVERED".equals(l.status())) delivered++;
            fin = fin.add(l.finalPrice());
            net = net.add(l.netOfVat());
            vat = vat.add(l.vat());
            agreed = agreed.add(l.agreedPrice());
            adj = adj.add(l.adjustmentsTotal());
            sug = sug.add(l.suggestedPrice());
            sav = sav.add(l.savings());
            com = com.add(l.commission());
            comVat = comVat.add(l.commissionVat());
            carrierNet = carrierNet.add(l.carrierNet());
            kg = kg.add(l.weightKg());
            km = km.add(l.distanceKm());
            if (l.vehicleCapacityKg() != null && l.vehicleCapacityKg().signum() > 0) {
                withCapacity++;
                occ = occ.add(l.weightKg().divide(l.vehicleCapacityKg(), 6, RoundingMode.HALF_UP).min(BigDecimal.ONE));
            }
        }
        BigDecimal occupancy = withCapacity == 0 ? null
                : occ.divide(BigDecimal.valueOf(withCapacity), 4, RoundingMode.HALF_UP);
        return new Totals(n, delivered, fin, net, vat, agreed, adj, sug, sav, com, comVat, carrierNet, kg, km, occupancy);
    }

    /** Agrupa por la clave dada; orden por monto final descendente (o por clave si {@code byKey}). */
    public static List<Group> groupBy(Collection<ReportLine> lines, Function<ReportLine, String> key, boolean byKey) {
        Map<String, List<ReportLine>> groups = new LinkedHashMap<>();
        for (ReportLine l : lines) groups.computeIfAbsent(key.apply(l), k -> new ArrayList<>()).add(l);
        List<Group> out = new ArrayList<>();
        groups.forEach((k, v) -> out.add(new Group(k, totals(v))));
        out.sort(byKey ? Comparator.comparing(Group::key)
                : Comparator.comparing((Group g) -> g.totals().finalTotal()).reversed().thenComparing(Group::key));
        return out;
    }

    /** Mes (yyyy-MM) de la adjudicación. */
    public static String month(ReportLine l) {
        return l.awardedOn().toString().substring(0, 7);
    }
}
