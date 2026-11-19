package pe.logyx.settlement.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.logyx.settlement.application.port.out.SettlementBootstrap;
import pe.logyx.settlement.application.port.out.SettlementReader;
import pe.logyx.settlement.application.port.out.SettlementRepository;
import pe.logyx.settlement.domain.model.CommissionStatus;
import pe.logyx.settlement.domain.model.PaymentMethod;
import pe.logyx.settlement.domain.model.Settlement;
import pe.logyx.settlement.domain.model.SettlementStatus;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * SQL nativo de {@code shipment_settlements} y {@code settlement_history}. Corre con la RLS de quien llama salvo
 * {@link #open}, que el servicio invoca dentro de {@code SystemTx}.
 */
@ApplicationScoped
public class NativeSettlementRepository implements SettlementRepository, SettlementReader, SettlementBootstrap {

    @Inject EntityManager em;

    private static final String COLUMNS = """
            shipment_id, requester_org_id, carrier_org_id, status, due_on, invoice_number, invoice_issued_on,
            invoice_document_id, invoiced_amount, invoiced_at, invoiced_by, payment_method, payment_operation, paid_on,
            paid_amount, payment_document_id, paid_at, paid_by, confirmed_at, confirmed_by, disputed_at,
            disputed_by_org_id, dispute_reason, status_before_dispute, commission_status, commission_collection_id
            """;

    // ---------- Bootstrap (sistema)

    @Override
    public int open(UUID shipmentId, int defaultTermsDays) {
        return em.createNativeQuery("""
                INSERT INTO shipment_settlements (shipment_id, requester_org_id, carrier_org_id, due_on, commission_status)
                SELECT s.id, s.requester_org_id, s.carrier_org_id,
                       (s.delivered_at AT TIME ZONE 'America/Lima')::date
                           + coalesce(op.payment_terms_days, CAST(?2 AS int)),
                       CASE WHEN coalesce(s.commission_rate, 0) > 0 THEN 'PENDING' ELSE 'NOT_APPLICABLE' END
                  FROM shipments s
                  LEFT JOIN organization_preferences op ON op.organization_id = s.requester_org_id
                 WHERE s.status = 'DELIVERED' AND s.delivered_at IS NOT NULL
                   AND (CAST(?1 AS uuid) IS NULL OR s.id = CAST(?1 AS uuid))
                ON CONFLICT (shipment_id) DO NOTHING
                """).setParameter(1, shipmentId == null ? null : shipmentId.toString())
                .setParameter(2, defaultTermsDays).executeUpdate();
    }

    @Override
    public boolean recordNotice(UUID shipmentId, String kind) {
        return em.createNativeQuery("INSERT INTO settlement_notices (shipment_id, kind) VALUES (?1, ?2) "
                + "ON CONFLICT DO NOTHING").setParameter(1, shipmentId).setParameter(2, kind).executeUpdate() == 1;
    }

    // ---------- Repositorio

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Settlement> find(UUID shipmentId) {
        List<Object[]> rows = em.createNativeQuery("SELECT " + COLUMNS + " FROM shipment_settlements WHERE shipment_id = ?1")
                .setParameter(1, shipmentId).getResultList();
        return rows.stream().findFirst().map(NativeSettlementRepository::map);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Settlement> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<Object[]> rows = em.createNativeQuery("SELECT " + COLUMNS
                        + " FROM shipment_settlements WHERE shipment_id = ANY (CAST(?1 AS uuid[]))")
                .setParameter(1, uuidArray(ids)).getResultList();
        return rows.stream().map(NativeSettlementRepository::map).toList();
    }

    @Override
    public void update(Settlement s) {
        var i = s.invoice();
        var p = s.payment();
        var d = s.dispute();
        Query q = em.createNativeQuery("""
                UPDATE shipment_settlements SET status = ?2, invoice_number = ?3, invoice_issued_on = ?4,
                       invoice_document_id = ?5, invoiced_amount = ?6, invoiced_at = ?7, invoiced_by = ?8,
                       payment_method = ?9, payment_operation = ?10, paid_on = ?11, paid_amount = ?12,
                       payment_document_id = ?13, paid_at = ?14, paid_by = ?15, confirmed_at = ?16, confirmed_by = ?17,
                       disputed_at = ?18, disputed_by_org_id = ?19, dispute_reason = ?20, status_before_dispute = ?21,
                       commission_status = ?22, commission_collection_id = ?23, updated_at = now()
                 WHERE shipment_id = ?1
                """);
        q.setParameter(1, s.shipmentId()).setParameter(2, s.status().name());
        q.setParameter(3, i == null ? null : i.number()).setParameter(4, i == null ? null : i.issuedOn());
        q.setParameter(5, i == null ? null : i.documentId()).setParameter(6, i == null ? null : i.amount());
        q.setParameter(7, i == null ? null : i.at()).setParameter(8, i == null ? null : i.by());
        q.setParameter(9, p == null ? null : p.method().name()).setParameter(10, p == null ? null : p.operationNumber());
        q.setParameter(11, p == null ? null : p.paidOn()).setParameter(12, p == null ? null : p.amount());
        q.setParameter(13, p == null ? null : p.documentId()).setParameter(14, p == null ? null : p.at());
        q.setParameter(15, p == null ? null : p.by());
        q.setParameter(16, s.confirmedAt()).setParameter(17, s.confirmedBy());
        q.setParameter(18, d == null ? null : d.at()).setParameter(19, d == null ? null : d.byOrgId());
        q.setParameter(20, d == null ? null : d.reason()).setParameter(21, d == null ? null : d.previous().name());
        q.setParameter(22, s.commissionStatus().name()).setParameter(23, s.commissionCollectionId());
        if (q.executeUpdate() != 1) throw new IllegalStateException("Cobro no actualizado: " + s.shipmentId());
    }

    @Override
    public void addHistory(Settlement s, SettlementStatus from, UUID actorProfileId, UUID actorOrgId, String note) {
        em.createNativeQuery("""
                INSERT INTO settlement_history (shipment_id, requester_org_id, carrier_org_id, from_status, to_status,
                                                actor_profile_id, actor_org_id, note)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                """).setParameter(1, s.shipmentId()).setParameter(2, s.requesterOrgId())
                .setParameter(3, s.carrierOrgId()).setParameter(4, from == null ? null : from.name())
                .setParameter(5, s.status().name()).setParameter(6, actorProfileId).setParameter(7, actorOrgId)
                .setParameter(8, note).executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<HistoryEntry> history(UUID shipmentId) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT h.from_status, h.to_status, h.actor_profile_id, p.full_name, h.actor_org_id, h.note, h.created_at
                  FROM settlement_history h LEFT JOIN profiles p ON p.id = h.actor_profile_id
                 WHERE h.shipment_id = ?1 ORDER BY h.created_at, h.id
                """).setParameter(1, shipmentId).getResultList();
        return rows.stream().map(r -> new HistoryEntry(r[0] == null ? null : SettlementStatus.valueOf((String) r[0]),
                SettlementStatus.valueOf((String) r[1]), uuid(r[2]), (String) r[3], uuid(r[4]), (String) r[5],
                odt(r[6]))).toList();
    }

    // ---------- Listados

    private static final String LIST_SELECT = """
            SELECT s.id, r.code, r.origin_city, r.destination_city, s.requester_org_id, rq.legal_name, s.carrier_org_id,
                   cr.legal_name, s.delivered_at, s.agreed_price, adj.acc, adj.pend, s.commission_rate,
                   coalesce(st.status, 'PENDING_INVOICE') AS eff_status,
                   coalesce(st.due_on, (s.delivered_at AT TIME ZONE 'America/Lima')::date + CAST(?1 AS int)) AS eff_due,
                   st.invoice_number, st.paid_on, st.paid_amount,
                   coalesce(st.commission_status,
                            CASE WHEN coalesce(s.commission_rate, 0) > 0 THEN 'PENDING' ELSE 'NOT_APPLICABLE' END) AS eff_comm
              FROM shipments s
              JOIN shipment_requests r ON r.id = s.request_id
              JOIN organizations rq ON rq.id = s.requester_org_id
              JOIN organizations cr ON cr.id = s.carrier_org_id
              LEFT JOIN shipment_settlements st ON st.shipment_id = s.id
              LEFT JOIN LATERAL (SELECT coalesce(sum(a.amount) FILTER (WHERE a.status = 'ACCEPTED'), 0) AS acc,
                                        count(*) FILTER (WHERE a.status = 'PENDING') AS pend
                                   FROM shipment_adjustments a WHERE a.shipment_id = s.id) adj ON true
             WHERE s.status = 'DELIVERED' AND s.delivered_at IS NOT NULL
            """;

    @Override
    @SuppressWarnings("unchecked")
    public Paged<Row> list(Filter f, PageRequest page) {
        Batch b = top(f, page.offset(), page.size());
        return new Paged<>(b.rows(), page, b.total());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Batch top(Filter f, int offset, int limit) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(f.defaultTermsDays());
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            params.add("{" + String.join(",", f.statuses()) + "}");
            where.append(" AND coalesce(st.status, 'PENDING_INVOICE') = ANY (CAST(?").append(params.size()).append(" AS text[]))");
        }
        if (Boolean.TRUE.equals(f.overdue())) {
            params.add(f.today());
            where.append(" AND coalesce(st.status, 'PENDING_INVOICE') IN ('PENDING_INVOICE', 'INVOICED')")
                    .append(" AND coalesce(st.due_on, (s.delivered_at AT TIME ZONE 'America/Lima')::date + CAST(?1 AS int)) < ?")
                    .append(params.size());
        }
        if (f.carrierOrgId() != null) {
            params.add(f.carrierOrgId());
            where.append(" AND s.carrier_org_id = ?").append(params.size());
        }
        if (Boolean.TRUE.equals(f.commissionPending())) {
            where.append(" AND coalesce(st.commission_status, CASE WHEN coalesce(s.commission_rate, 0) > 0 THEN 'PENDING'")
                    .append(" ELSE 'NOT_APPLICABLE' END) = 'PENDING'");
        }
        if (f.from() != null) {
            params.add(f.from());
            where.append(" AND (s.delivered_at AT TIME ZONE 'America/Lima')::date >= ?").append(params.size());
        }
        if (f.to() != null) {
            params.add(f.to());
            where.append(" AND (s.delivered_at AT TIME ZONE 'America/Lima')::date <= ?").append(params.size());
        }
        Query count = em.createNativeQuery("SELECT count(*) FROM (" + LIST_SELECT + where + ") x");
        Query q = em.createNativeQuery(LIST_SELECT + where
                + " ORDER BY CASE WHEN coalesce(st.status, 'PENDING_INVOICE') IN ('PENDING_INVOICE', 'INVOICED', 'DISPUTED')"
                + " THEN 0 ELSE 1 END, eff_due ASC, s.delivered_at DESC, s.id"
                + " LIMIT " + Math.max(1, limit) + " OFFSET " + Math.max(0, offset));
        for (int i = 0; i < params.size(); i++) {
            count.setParameter(i + 1, params.get(i));
            q.setParameter(i + 1, params.get(i));
        }
        long total = ((Number) count.getSingleResult()).longValue();
        List<Object[]> rows = q.getResultList();
        return new Batch(rows.stream().map(NativeSettlementRepository::row).toList(), total);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Row> rows(Collection<UUID> shipmentIds, int defaultTermsDays) {
        if (shipmentIds.isEmpty()) return List.of();
        List<Object[]> rows = em.createNativeQuery(LIST_SELECT + " AND s.id = ANY (CAST(?2 AS uuid[])) ORDER BY s.delivered_at")
                .setParameter(1, defaultTermsDays).setParameter(2, uuidArray(shipmentIds)).getResultList();
        return rows.stream().map(NativeSettlementRepository::row).toList();
    }

    // ---------- Mapeo

    private static Row row(Object[] r) {
        return new Row(uuid(r[0]), (String) r[1], (String) r[2], (String) r[3], uuid(r[4]), (String) r[5], uuid(r[6]),
                (String) r[7], odt(r[8]), dec(r[9]), dec(r[10]), ((Number) r[11]).longValue(), dec(r[12]),
                (String) r[13], date(r[14]), (String) r[15], date(r[16]), dec(r[17]), (String) r[18]);
    }

    private static Settlement map(Object[] r) {
        Settlement.Invoice invoice = r[5] == null ? null : new Settlement.Invoice((String) r[5], date(r[6]), uuid(r[7]),
                dec(r[8]), odt(r[9]), uuid(r[10]));
        Settlement.Payment payment = r[11] == null ? null : new Settlement.Payment(PaymentMethod.valueOf((String) r[11]),
                (String) r[12], date(r[13]), dec(r[14]), uuid(r[15]), odt(r[16]), uuid(r[17]));
        Settlement.Dispute dispute = r[20] == null || r[23] == null ? null : new Settlement.Dispute(odt(r[20]),
                uuid(r[21]), (String) r[22], SettlementStatus.valueOf((String) r[23]));
        return new Settlement(uuid(r[0]), uuid(r[1]), uuid(r[2]), SettlementStatus.valueOf((String) r[3]), date(r[4]),
                invoice, payment, odt(r[18]), uuid(r[19]), dispute, CommissionStatus.valueOf((String) r[24]),
                uuid(r[25]), true);
    }

    static String uuidArray(Collection<UUID> ids) {
        StringJoiner j = new StringJoiner(",", "{", "}");
        ids.forEach(id -> j.add(id.toString()));
        return j.toString();
    }

    static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    static BigDecimal dec(Object o) {
        return o == null ? null : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    static LocalDate date(Object o) {
        if (o == null) return null;
        return o instanceof LocalDate l ? l : o instanceof Date d ? d.toLocalDate() : LocalDate.parse(o.toString());
    }

    static OffsetDateTime odt(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(v.toString());
    }
}
