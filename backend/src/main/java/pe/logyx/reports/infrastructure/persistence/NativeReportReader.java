package pe.logyx.reports.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.logyx.reports.application.port.out.ReportReader;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL nativo de solo lectura bajo la RLS de quien consulta (shipments, solicitudes, ajustes, cobros, flota propia). */
@ApplicationScoped
public class NativeReportReader implements ReportReader {

    @Inject EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Row> rows(Basis basis, LocalDate from, LocalDate to, UUID organizationId, int limit) {
        String date = basis == Basis.DELIVERED ? "(s.delivered_at AT TIME ZONE 'America/Lima')::date"
                : "(s.created_at AT TIME ZONE 'America/Lima')::date";
        String sql = """
                SELECT s.id, r.code, s.created_at, s.delivered_at, s.status, s.requester_org_id, rq.legal_name,
                       s.carrier_org_id, cr.legal_name, r.origin_city, r.destination_city, r.cargo_type, r.weight_kg,
                       r.distance_km, r.suggested_price, s.agreed_price, coalesce(adj.acc, 0), s.commission_rate,
                       s.vehicle_id, s.vehicle_plate, v.max_weight_kg,
                       CASE WHEN s.status = 'DELIVERED' THEN coalesce(st.status, 'PENDING_INVOICE') END
                  FROM shipments s
                  JOIN shipment_requests r ON r.id = s.request_id
                  JOIN organizations rq ON rq.id = s.requester_org_id
                  JOIN organizations cr ON cr.id = s.carrier_org_id
                  LEFT JOIN vehicles v ON v.id = s.vehicle_id
                  LEFT JOIN shipment_settlements st ON st.shipment_id = s.id
                  LEFT JOIN LATERAL (SELECT sum(a.amount) AS acc FROM shipment_adjustments a
                                      WHERE a.shipment_id = s.id AND a.status = 'ACCEPTED') adj ON true
                 WHERE s.status <> 'CANCELLED'
                """ + (basis == Basis.DELIVERED ? " AND s.status = 'DELIVERED' AND s.delivered_at IS NOT NULL" : "")
                + " AND " + date + " BETWEEN ?1 AND ?2"
                + (organizationId == null ? "" : " AND (s.requester_org_id = ?3 OR s.carrier_org_id = ?3)")
                + " ORDER BY " + (basis == Basis.DELIVERED ? "s.delivered_at" : "s.created_at") + ", s.id LIMIT " + limit;
        Query q = em.createNativeQuery(sql).setParameter(1, from).setParameter(2, to);
        if (organizationId != null) q.setParameter(3, organizationId);
        List<Object[]> rows = q.getResultList();
        return rows.stream().map(r -> new Row(uuid(r[0]), (String) r[1], odt(r[2]), odt(r[3]), (String) r[4],
                uuid(r[5]), (String) r[6], uuid(r[7]), (String) r[8], (String) r[9], (String) r[10], (String) r[11],
                dec(r[12]), dec(r[13]), dec(r[14]), dec(r[15]), dec(r[16]), dec(r[17]), uuid(r[18]), (String) r[19],
                dec(r[20]), (String) r[21])).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BidStats> bidStats(UUID carrierOrgId, LocalDate from, LocalDate to) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT r.origin_city, r.destination_city, count(*), count(*) FILTER (WHERE b.status = 'ACCEPTED')
                  FROM bids b JOIN shipment_requests r ON r.id = b.request_id
                 WHERE b.carrier_org_id = ?1
                   AND (b.created_at AT TIME ZONE 'America/Lima')::date BETWEEN ?2 AND ?3
                 GROUP BY r.origin_city, r.destination_city
                 ORDER BY count(*) DESC, r.origin_city, r.destination_city
                """).setParameter(1, carrierOrgId).setParameter(2, from).setParameter(3, to).getResultList();
        return rows.stream().map(r -> new BidStats((String) r[0], (String) r[1], ((Number) r[2]).longValue(),
                ((Number) r[3]).longValue())).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<OrgInfo> organization(UUID id) {
        List<Object[]> rows = em.createNativeQuery("SELECT id, legal_name, ruc, type FROM organizations WHERE id = ?1")
                .setParameter(1, id).getResultList();
        return rows.stream().findFirst().map(r -> new OrgInfo(uuid(r[0]), (String) r[1], ((String) r[2]).trim(),
                (String) r[3]));
    }

    private static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static BigDecimal dec(Object o) {
        return o == null ? null : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static OffsetDateTime odt(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(v.toString());
    }
}
