package pe.logyx.operator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Convierte los datos crudos en señales con severidad (roadmap 2.15). Son indicios para revisar, no sanciones: el
 * operador decide. Datos compartidos: ALTA si hay 3+ organizaciones o si es la IP del registro; tasas: ALTA si superan
 * 1.5 × el umbral.
 */
public final class FraudSignalPolicy {

    /** {@code value}: el dato repetido (teléfono, placa, licencia) o la tasa; nunca la IP (solo su huella). */
    public record Signal(FraudSignalKind kind, FraudSignalSeverity severity, List<UUID> organizationIds, String value,
                         BigDecimal rate, long sample) {}

    /** Mismo dato en varias organizaciones. */
    public record Shared(FraudSignalKind kind, String value, List<UUID> organizationIds) {}

    /** Tasa de una organización: {@code hits} de {@code total}. */
    public record Rate(FraudSignalKind kind, UUID organizationId, long hits, long total) {}

    /** Cuenta nueva con ofertas bajo el sugerido. */
    public record LowBids(UUID organizationId, long lowBids, long totalBids) {}

    private FraudSignalPolicy() {}

    public static List<Signal> evaluate(List<Shared> shared, List<Rate> rates, List<LowBids> lowBids,
                                        BigDecimal rateThreshold, long minSample, long lowBidCount) {
        List<Signal> out = new ArrayList<>();
        for (Shared s : shared) {
            if (s.organizationIds().size() < 2) continue;
            boolean high = s.organizationIds().size() >= 3 || s.kind() == FraudSignalKind.SHARED_SIGNUP_IP;
            out.add(new Signal(s.kind(), high ? FraudSignalSeverity.HIGH : FraudSignalSeverity.MEDIUM, s.organizationIds(),
                    s.kind() == FraudSignalKind.SHARED_SIGNUP_IP ? null : s.value(), null, s.organizationIds().size()));
        }
        for (Rate r : rates) {
            if (r.total() < minSample || r.total() == 0) continue;
            BigDecimal rate = BigDecimal.valueOf(r.hits()).divide(BigDecimal.valueOf(r.total()), 4, RoundingMode.HALF_UP);
            if (rate.compareTo(rateThreshold) < 0) continue;
            boolean high = rate.compareTo(rateThreshold.multiply(new BigDecimal("1.5"))) >= 0;
            out.add(new Signal(r.kind(), high ? FraudSignalSeverity.HIGH : FraudSignalSeverity.MEDIUM, List.of(r.organizationId()), null,
                    rate, r.total()));
        }
        for (LowBids l : lowBids) {
            if (l.lowBids() < lowBidCount) continue;
            BigDecimal rate = BigDecimal.valueOf(l.lowBids())
                    .divide(BigDecimal.valueOf(Math.max(1, l.totalBids())), 4, RoundingMode.HALF_UP);
            out.add(new Signal(FraudSignalKind.LOW_BIDS_NEW_ACCOUNT,
                    l.lowBids() >= 2 * lowBidCount ? FraudSignalSeverity.HIGH : FraudSignalSeverity.MEDIUM, List.of(l.organizationId()), null,
                    rate, l.totalBids()));
        }
        out.sort(Comparator.comparing(Signal::severity).thenComparing(Signal::kind));
        return out;
    }
}
