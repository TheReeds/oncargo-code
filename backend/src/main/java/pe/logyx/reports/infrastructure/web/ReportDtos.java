package pe.logyx.reports.infrastructure.web;

import pe.logyx.reports.application.ReportService;
import pe.logyx.reports.application.port.out.ReportReader;
import pe.logyx.reports.domain.ReportAggregator;
import pe.logyx.reports.domain.ReportLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs de reportes (2.11). Montos en soles con IGV salvo {@code netOfVat}. La comisión, su IGV, el neto del
 * transportista, la ocupación y el neto por km son nulos para la PYME.
 */
public final class ReportDtos {

    private ReportDtos() {}

    /**
     * Totales. {@code savings} = sugerido − acordado (lo que bajó la subasta); {@code savingsRate} 0–1;
     * {@code costPerKg} = precio final / kg; {@code occupancy} = peso / capacidad del vehículo (0–1, transportista).
     */
    public record ReportTotalsView(long shipments, long delivered, BigDecimal finalTotal, BigDecimal netOfVat,
                                   BigDecimal vat, BigDecimal agreedTotal, BigDecimal adjustmentsTotal,
                                   BigDecimal suggestedTotal, BigDecimal savings, BigDecimal savingsRate,
                                   BigDecimal costPerKg, BigDecimal weightKg, BigDecimal distanceKm,
                                   BigDecimal commission, BigDecimal commissionVat, BigDecimal carrierNet,
                                   BigDecimal netPerKm, BigDecimal occupancy) {
        static ReportTotalsView of(ReportAggregator.Totals t) {
            return new ReportTotalsView(t.shipments(), t.delivered(), t.finalTotal(), t.netOfVat(), t.vat(),
                    t.agreedTotal(), t.adjustmentsTotal(), t.suggestedTotal(), t.savings(), t.savingsRate(),
                    t.costPerKg().setScale(2, RoundingMode.HALF_UP), t.weightKg(), t.distanceKm(), t.commission(),
                    t.commissionVat(), t.carrierNet(), t.carrierNet() == null ? null : t.netPerKm(), t.occupancy());
        }
    }

    /** Un grupo: {@code key} (mes yyyy-MM, ruta ORIGEN-DESTINO, id de organización o vehículo, tipo de carga) y su nombre. */
    public record ReportGroupView(String key, String label, ReportTotalsView totals) {}

    /** Ofertas del transportista por ruta y cuántas ganó (tasa de adjudicación). */
    public record ReportBidRouteView(String originCity, String destinationCity, long bids, long won, BigDecimal winRate) {
        static ReportBidRouteView of(ReportReader.BidStats b) {
            return new ReportBidRouteView(b.originCity(), b.destinationCity(), b.bids(), b.won(), b.bids() == 0
                    ? BigDecimal.ZERO : BigDecimal.valueOf(b.won()).divide(BigDecimal.valueOf(b.bids()), 4, RoundingMode.HALF_UP));
        }
    }

    /**
     * Reporte del período (por fecha de adjudicación, envíos no cancelados). {@code byCounterparty}: transportistas para
     * la PYME y el operador, PYMEs para el transportista. {@code byVehicle} y {@code bidsByRoute} solo para el
     * transportista. {@code truncated}: se leyeron los primeros 20 000 envíos.
     */
    public record ReportOverviewView(LocalDate from, LocalDate to, String viewer, ReportTotalsView totals,
                                     List<ReportGroupView> byMonth, List<ReportGroupView> byRoute,
                                     List<ReportGroupView> byCounterparty, List<ReportGroupView> byCargoType,
                                     List<ReportGroupView> byVehicle, List<ReportBidRouteView> bidsByRoute,
                                     boolean truncated) {
        static ReportOverviewView of(ReportService.Overview o) {
            Map<String, String> names = o.names();
            return new ReportOverviewView(o.from(), o.to(), o.viewer().name(), ReportTotalsView.of(o.totals()),
                    groups(o.byMonth(), Map.of()), groups(o.byRoute(), Map.of()), groups(o.byCounterparty(), names),
                    groups(o.byCargoType(), Map.of()), groups(o.byVehicle(), names),
                    o.bidsByRoute().stream().map(ReportBidRouteView::of).toList(), o.truncated());
        }

        private static List<ReportGroupView> groups(List<ReportAggregator.Group> gs, Map<String, String> names) {
            return gs.stream().map(g -> new ReportGroupView(g.key(), names.getOrDefault(g.key(), g.key()),
                    ReportTotalsView.of(g.totals()))).toList();
        }
    }

    /** Línea de una liquidación o resumen: un envío entregado en el mes. */
    public record StatementLineView(UUID shipmentId, String code, LocalDate deliveredOn, String originCity,
                                    String destinationCity, String counterpartyName, BigDecimal agreedPrice,
                                    BigDecimal adjustmentsTotal, BigDecimal finalPrice, BigDecimal netOfVat,
                                    BigDecimal vat, BigDecimal commissionRate, BigDecimal commission,
                                    BigDecimal commissionVat, BigDecimal carrierNet, String settlementStatus) {
        static StatementLineView of(ReportLine l, boolean carrier) {
            return new StatementLineView(l.shipmentId(), l.code(), l.deliveredOn(), l.originCity(), l.destinationCity(),
                    carrier ? l.requesterName() : l.carrierName(), l.agreedPrice(), l.adjustmentsTotal(), l.finalPrice(),
                    l.netOfVat(), l.vat(), l.commissionRate(), l.commission(), l.commissionVat(), l.carrierNet(),
                    l.settlementStatus());
        }
    }

    /**
     * Documento interno del mes (no es comprobante de pago SUNAT). {@code kind}: {@code COMMISSION_STATEMENT}
     * (liquidación de comisión al transportista) o {@code COST_SUMMARY} (resumen de costos de la PYME).
     */
    public record StatementView(String number, String kind, String period, String periodLabel, OffsetDateTime generatedAt,
                                UUID organizationId, String organizationName, String organizationRuc,
                                String issuerName, String issuerRuc, List<StatementLineView> lines,
                                ReportTotalsView totals, boolean truncated, String disclaimer) {
        static StatementView of(ReportService.Statement s) {
            boolean carrier = "COMMISSION_STATEMENT".equals(s.kind());
            return new StatementView(s.number(), s.kind(), s.period().month().toString(), s.period().label(),
                    s.generatedAt(), s.organization().id(), s.organization().legalName(), s.organization().ruc(),
                    s.issuerName(), s.issuerRuc(), s.lines().stream().map(l -> StatementLineView.of(l, carrier)).toList(),
                    ReportTotalsView.of(s.totals()), s.truncated(),
                    "Documento interno de OnCargo. No es un comprobante de pago electrónico (SUNAT).");
        }
    }
}
