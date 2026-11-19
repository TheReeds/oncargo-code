package pe.logyx.operator.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.operator.application.port.out.FraudSignalReader;
import pe.logyx.operator.domain.FraudSignalKind;
import pe.logyx.operator.domain.FraudSignalPolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * SQL nativo de solo lectura de las señales de fraude (2.15). Corre bajo la RLS del operador ({@code orgs_read},
 * {@code vehicles_operator}, {@code drivers_operator}, {@code bids_operator}, {@code shipments_operator},
 * {@code requests_operator}); nunca SystemTx. Las organizaciones operadoras no cuentan.
 */
@ApplicationScoped
public class NativeFraudSignalReader implements FraudSignalReader {

    @Inject EntityManager em;

    @Override
    public List<FraudSignalPolicy.Shared> sharedData() {
        List<FraudSignalPolicy.Shared> out = new ArrayList<>();
        out.addAll(shared(FraudSignalKind.SHARED_PHONE, """
                SELECT right(regexp_replace(phone, '[^0-9]', '', 'g'), 9) AS v, string_agg(id::text, ',' ORDER BY created_at)
                  FROM organizations
                 WHERE phone IS NOT NULL AND type <> 'OPERATOR' AND length(regexp_replace(phone, '[^0-9]', '', 'g')) >= 9
                 GROUP BY 1 HAVING count(*) > 1 ORDER BY count(*) DESC LIMIT 100"""));
        out.addAll(shared(FraudSignalKind.SHARED_PLATE, """
                SELECT upper(regexp_replace(v.plate, '[^A-Za-z0-9]', '', 'g')) AS p,
                       string_agg(DISTINCT v.organization_id::text, ',')
                  FROM vehicles v
                 GROUP BY 1 HAVING count(DISTINCT v.organization_id) > 1 LIMIT 100"""));
        out.addAll(shared(FraudSignalKind.SHARED_LICENSE, """
                SELECT upper(regexp_replace(d.license_number, '[^A-Za-z0-9]', '', 'g')) AS l,
                       string_agg(DISTINCT d.organization_id::text, ',')
                  FROM drivers d
                 GROUP BY 1 HAVING count(DISTINCT d.organization_id) > 1 LIMIT 100"""));
        out.addAll(shared(FraudSignalKind.SHARED_SIGNUP_IP, """
                SELECT signup_ip_hash, string_agg(id::text, ',' ORDER BY created_at)
                  FROM organizations
                 WHERE signup_ip_hash IS NOT NULL AND type <> 'OPERATOR'
                 GROUP BY 1 HAVING count(*) > 1 LIMIT 100"""));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<FraudSignalPolicy.Shared> shared(FraudSignalKind kind, String sql) {
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(r -> new FraudSignalPolicy.Shared(kind, (String) r[0], ids((String) r[1]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FraudSignalPolicy.Rate> withdrawalRates(long minSample) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT b.carrier_org_id, count(*) FILTER (WHERE b.status = 'WITHDRAWN'), count(*)
                  FROM bids b
                 WHERE b.assigned_by_operator IS NOT TRUE
                 GROUP BY b.carrier_org_id HAVING count(*) >= ?1""").setParameter(1, minSample).getResultList();
        return rows.stream().map(r -> new FraudSignalPolicy.Rate(FraudSignalKind.HIGH_WITHDRAWAL_RATE, (UUID) r[0],
                num(r[1]), num(r[2]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FraudSignalPolicy.Rate> cancellationRates(long minSample) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT o.id,
                       (SELECT count(*) FROM shipments s WHERE s.cancelled_by_org_id = o.id),
                       (SELECT count(*) FROM shipments s WHERE s.requester_org_id = o.id OR s.carrier_org_id = o.id)
                  FROM organizations o
                 WHERE o.type <> 'OPERATOR'
                   AND (SELECT count(*) FROM shipments s WHERE s.requester_org_id = o.id OR s.carrier_org_id = o.id) >= ?1
                """).setParameter(1, minSample).getResultList();
        return rows.stream().map(r -> new FraudSignalPolicy.Rate(FraudSignalKind.HIGH_CANCELLATION_RATE, (UUID) r[0],
                num(r[1]), num(r[2]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FraudSignalPolicy.LowBids> lowBidsFromNewAccounts(OffsetDateTime since, BigDecimal ratio) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT o.id, count(*) FILTER (WHERE b.amount < r.suggested_price * ?2), count(*)
                  FROM organizations o
                  JOIN bids b ON b.carrier_org_id = o.id
                  JOIN shipment_requests r ON r.id = b.request_id
                 WHERE o.type = 'CARRIER' AND o.created_at >= ?1
                 GROUP BY o.id""").setParameter(1, since).setParameter(2, ratio).getResultList();
        return rows.stream().map(r -> new FraudSignalPolicy.LowBids((UUID) r[0], num(r[1]), num(r[2]))).toList();
    }

    private static List<UUID> ids(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).distinct()
                .map(UUID::fromString).toList();
    }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
}
