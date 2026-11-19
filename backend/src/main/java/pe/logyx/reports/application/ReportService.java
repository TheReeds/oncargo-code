package pe.logyx.reports.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.pricing.application.CommissionService;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.reports.application.port.out.ReportReader;
import pe.logyx.reports.domain.ReportAggregator;
import pe.logyx.reports.domain.ReportLine;
import pe.logyx.reports.domain.StatementPeriod;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.domain.AdjustmentPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Reportes, exportación y documentos internos (roadmap 2.11). Todo bajo la RLS de quien consulta: la PYME y el
 * transportista ven sus envíos; el operador, todos. <b>Los montos usan el precio final</b> (acordado + ajustes
 * aceptados, ADR-0012) con la comisión y su IGV calculados con la tasa guardada en el envío (ADR-0010).
 *
 * <p>«Facturas» aquí son documentos internos (no comprobantes electrónicos SUNAT, que son Ola 3): la liquidación de
 * comisión de OnCargo al transportista y el resumen de costos de la PYME, por mes de entrega. La web los muestra como
 * HTML imprimible (ADR-0014 §4).
 */
@ApplicationScoped
public class ReportService {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Envíos leídos por consulta (un reporte de un año de una PYME grande cabe de sobra). */
    public static final int MAX_LINES = 20_000;
    public static final int MAX_RANGE_DAYS = 400;
    public static final int EXPORT_MAX_ROWS = 10_000;

    /** Reporte del período. Las listas que no aplican al rol vienen vacías. */
    public record Overview(LocalDate from, LocalDate to, OrgType viewer, ReportAggregator.Totals totals,
                           List<ReportAggregator.Group> byMonth, List<ReportAggregator.Group> byRoute,
                           List<ReportAggregator.Group> byCounterparty, List<ReportAggregator.Group> byCargoType,
                           List<ReportAggregator.Group> byVehicle, List<ReportReader.BidStats> bidsByRoute,
                           java.util.Map<String, String> names, boolean truncated) {}

    /** Liquidación (transportista) o resumen de costos (PYME) de un mes. */
    public record Statement(String number, String kind, StatementPeriod period, OffsetDateTime generatedAt,
                            ReportReader.OrgInfo organization, String issuerName, String issuerRuc,
                            List<ReportLine> lines, ReportAggregator.Totals totals, boolean truncated) {}

    @Inject ReportReader reader;
    @Inject CommissionService commission;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "logyx.billing.issuer-name", defaultValue = "OnCargo")
    String issuerName;
    @ConfigProperty(name = "logyx.billing.issuer-ruc")
    java.util.Optional<String> issuerRuc;

    public static LocalDate today() { return LocalDate.now(LIMA); }

    @Transactional
    public Overview overview(LocalDate from, LocalDate to) {
        rejectDriver();
        LocalDate[] range = range(from, to);
        List<ReportLine> lines = lines(ReportReader.Basis.AWARDED, range[0], range[1], null);
        boolean truncated = lines.size() > MAX_LINES;
        if (truncated) lines = lines.subList(0, MAX_LINES);
        OrgType viewer = tenant.orgType();
        java.util.Map<String, String> names = new java.util.HashMap<>();
        lines.forEach(l -> {
            names.put(l.requesterOrgId().toString(), l.requesterName());
            names.put(l.carrierOrgId().toString(), l.carrierName());
            if (l.vehicleId() != null && l.vehiclePlate() != null) names.put(l.vehicleId().toString(), l.vehiclePlate());
        });
        var byMonth = ReportAggregator.groupBy(lines, ReportAggregator::month, true);
        var byRoute = ReportAggregator.groupBy(lines, ReportLine::route, false);
        var byCargo = ReportAggregator.groupBy(lines, ReportLine::cargoType, false);
        var byCounterparty = ReportAggregator.groupBy(lines, l -> (viewer == OrgType.CARRIER
                ? l.requesterOrgId() : l.carrierOrgId()).toString(), false);
        var byVehicle = viewer == OrgType.CARRIER
                ? ReportAggregator.groupBy(lines.stream().filter(l -> l.vehicleId() != null).toList(),
                l -> l.vehicleId().toString(), false) : List.<ReportAggregator.Group>of();
        var bids = viewer == OrgType.CARRIER ? reader.bidStats(tenant.orgId(), range[0], range[1])
                : List.<ReportReader.BidStats>of();
        var totals = ReportAggregator.totals(lines);
        if (viewer == OrgType.PYME) {
            totals = hideCommission(totals);
            byMonth = hide(byMonth);
            byRoute = hide(byRoute);
            byCargo = hide(byCargo);
            byCounterparty = hide(byCounterparty);
        }
        return new Overview(range[0], range[1], viewer, totals, byMonth, byRoute, byCounterparty, byCargo, byVehicle,
                bids, names, truncated);
    }

    /** Envíos del período para el CSV (misma consulta y RLS que el reporte). {@code LOGYX-EXP-001} si son demasiados. */
    @Transactional
    public List<ReportLine> export(LocalDate from, LocalDate to) {
        rejectDriver();
        LocalDate[] range = range(from, to);
        List<ReportLine> lines = lines(ReportReader.Basis.AWARDED, range[0], range[1], null);
        if (lines.size() > EXPORT_MAX_ROWS) {
            throw BusinessException.badRequest("LOGYX-EXP-001", "La exportación supera " + EXPORT_MAX_ROWS
                    + " filas: acota las fechas", "from");
        }
        return lines;
    }

    /**
     * Documento interno del mes (por fecha de entrega). Transportista: liquidación de comisión ({@code LIQ-…}). PYME:
     * resumen de costos ({@code RES-…}). Operador: el de la organización {@code organizationId} (obligatorio).
     */
    @Transactional
    public Statement statement(String rawPeriod, UUID organizationId) {
        rejectDriver();
        StatementPeriod period = StatementPeriod.parse(rawPeriod, today());
        UUID org;
        if (tenant.orgType() == OrgType.OPERATOR) {
            if (organizationId == null) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Elige la organización", "organizationId");
            }
            org = organizationId;
        } else {
            org = tenant.orgId();
        }
        ReportReader.OrgInfo info = reader.organization(org).orElseThrow(() -> BusinessException.notFound("Organización"));
        boolean carrier = "CARRIER".equals(info.type());
        if (!carrier && !"PYME".equals(info.type())) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Solo PYMEs y transportistas tienen liquidación", "organizationId");
        }
        List<ReportLine> lines = lines(ReportReader.Basis.DELIVERED, period.first(), period.last(), org).stream()
                .filter(l -> carrier ? org.equals(l.carrierOrgId()) : org.equals(l.requesterOrgId())).toList();
        boolean truncated = lines.size() > MAX_LINES;
        if (truncated) lines = lines.subList(0, MAX_LINES);
        var totals = ReportAggregator.totals(lines);
        boolean hide = !carrier || tenant.orgType() == OrgType.PYME;
        if (hide) {
            totals = hideCommission(totals);
            lines = lines.stream().map(ReportService::hideCommission).toList();
        }
        String suffix = org.toString().substring(0, 8).toUpperCase();
        String number = (carrier ? "LIQ-" : "RES-") + period.compact() + "-" + suffix;
        return new Statement(number, carrier ? "COMMISSION_STATEMENT" : "COST_SUMMARY", period, OffsetDateTime.now(),
                info, issuerName, issuerRuc.filter(r -> !r.isBlank()).orElse(null), lines, totals, truncated);
    }

    // ---------- Ayudas

    private List<ReportLine> lines(ReportReader.Basis basis, LocalDate from, LocalDate to, UUID org) {
        return reader.rows(basis, from, to, org, MAX_LINES + 1).stream().map(this::line).toList();
    }

    ReportLine line(ReportReader.Row r) {
        BigDecimal adj = r.acceptedAdjustments() == null ? BigDecimal.ZERO : r.acceptedAdjustments();
        BigDecimal finalPrice = AdjustmentPolicy.finalPrice(r.agreedPrice(), adj);
        BigDecimal rate = r.commissionRate() == null ? BigDecimal.ZERO : r.commissionRate();
        AwardedPriceBreakdown b = commission.breakdown(finalPrice, rate);
        return new ReportLine(r.shipmentId(), r.code(), r.awardedAt().atZoneSameInstant(LIMA).toLocalDate(),
                r.deliveredAt() == null ? null : r.deliveredAt().atZoneSameInstant(LIMA).toLocalDate(), r.status(),
                r.requesterOrgId(), r.requesterName(), r.carrierOrgId(), r.carrierName(), r.originCity(),
                r.destinationCity(), r.cargoType(), r.weightKg(), r.distanceKm(), r.suggestedPrice(), r.agreedPrice(),
                adj, finalPrice, b.netOfVat(), b.vat(), r.commissionRate(), b.commission(), b.commissionVat(),
                b.carrierNet(), r.vehicleId(), r.vehiclePlate(), r.vehicleCapacityKg(), r.settlementStatus());
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? today() : to;
        LocalDate f = from == null ? t.withDayOfMonth(1).minusMonths(5) : from;
        if (t.isBefore(f) || f.plusDays(MAX_RANGE_DAYS).isBefore(t)) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "Elige un rango de hasta " + MAX_RANGE_DAYS + " días", "to");
        }
        return new LocalDate[]{f, t};
    }

    private static ReportAggregator.Totals hideCommission(ReportAggregator.Totals t) {
        return new ReportAggregator.Totals(t.shipments(), t.delivered(), t.finalTotal(), t.netOfVat(), t.vat(),
                t.agreedTotal(), t.adjustmentsTotal(), t.suggestedTotal(), t.savings(), null, null, null, t.weightKg(),
                t.distanceKm(), null);
    }

    private static List<ReportAggregator.Group> hide(List<ReportAggregator.Group> groups) {
        return groups.stream().map(g -> new ReportAggregator.Group(g.key(), hideCommission(g.totals()))).toList();
    }

    private static ReportLine hideCommission(ReportLine l) {
        return new ReportLine(l.shipmentId(), l.code(), l.awardedOn(), l.deliveredOn(), l.status(), l.requesterOrgId(),
                l.requesterName(), l.carrierOrgId(), l.carrierName(), l.originCity(), l.destinationCity(), l.cargoType(),
                l.weightKg(), l.distanceKm(), l.suggestedPrice(), l.agreedPrice(), l.adjustmentsTotal(), l.finalPrice(),
                l.netOfVat(), l.vat(), null, null, null, null, l.vehicleId(), l.vehiclePlate(), null, l.settlementStatus());
    }

    private void rejectDriver() {
        if (tenant.isDriver()) throw BusinessException.forbidden("Los reportes son de la oficina");
    }
}
