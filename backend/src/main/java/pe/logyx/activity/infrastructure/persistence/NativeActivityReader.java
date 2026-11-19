package pe.logyx.activity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.activity.application.port.out.ActivityReader;
import pe.logyx.activity.domain.model.ActivityEntry;
import pe.logyx.activity.domain.model.ActivitySubject;
import pe.logyx.activity.domain.model.Author;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL nativo de solo lectura, en la transacción del usuario (RLS). Une {@code audit_log} (RLS {@code audit_own_read}
 * / {@code audit_operator}) con los recursos hijos visibles y hace LEFT JOIN a {@code profiles} (RLS: solo la propia
 * organización, o todas para el operador) para el nombre de quien actuó; {@code bids.created_by} (V16) agrega
 * «Oferta enviada», que la auditoría no registra.
 */
@ApplicationScoped
public class NativeActivityReader implements ActivityReader {

    @Inject EntityManager em;

    /** (entity, entity_id) del recurso y sus hijos, según el sujeto. Parámetro :id. */
    private static String entities(ActivitySubject s) {
        return switch (s) {
            case REQUEST -> """
                    SELECT 'shipment_request' AS entity, CAST(:id AS uuid) AS entity_id
                    UNION ALL SELECT 'bid', b.id FROM bids b WHERE b.request_id = CAST(:id AS uuid)
                    UNION ALL SELECT 'counter_offer', c.id FROM counter_offers c WHERE c.request_id = CAST(:id AS uuid)""";
            case BID -> """
                    SELECT 'bid' AS entity, CAST(:id AS uuid) AS entity_id
                    UNION ALL SELECT 'counter_offer', c.id FROM counter_offers c WHERE c.bid_id = CAST(:id AS uuid)""";
            case SHIPMENT -> """
                    SELECT 'shipment' AS entity, CAST(:id AS uuid) AS entity_id
                    UNION ALL SELECT 'bid', s.bid_id FROM shipments s WHERE s.id = CAST(:id AS uuid)
                    UNION ALL SELECT 'incident', i.id FROM incidents i WHERE i.shipment_id = CAST(:id AS uuid)
                    UNION ALL SELECT 'document', d.id FROM documents d WHERE d.shipment_id = CAST(:id AS uuid)
                    UNION ALL SELECT 'review', r.id FROM reviews r WHERE r.shipment_id = CAST(:id AS uuid)""";
            case TRIP -> """
                    SELECT 'carrier_trip' AS entity, CAST(:id AS uuid) AS entity_id
                    UNION ALL SELECT 'trip_booking', t.id FROM trip_bookings t WHERE t.trip_id = CAST(:id AS uuid)""";
            case INCIDENT -> "SELECT 'incident' AS entity, CAST(:id AS uuid) AS entity_id";
        };
    }

    /** Ofertas del sujeto cuya autoría (V16) se muestra como «Oferta enviada». */
    private static String bidsOf(ActivitySubject s) {
        return switch (s) {
            case REQUEST -> "b.request_id = CAST(:id AS uuid)";
            case BID -> "b.id = CAST(:id AS uuid)";
            case SHIPMENT -> "b.id = (SELECT s.bid_id FROM shipments s WHERE s.id = CAST(:id AS uuid))";
            default -> null;
        };
    }

    @Override
    public boolean visible(ActivitySubject s, UUID id) {
        String table = switch (s) {
            case REQUEST -> "shipment_requests";
            case BID -> "bids";
            case SHIPMENT -> "shipments";
            case TRIP -> "carrier_trips";
            case INCIDENT -> "incidents";
        };
        Object v = em.createNativeQuery("SELECT count(*) FROM " + table + " WHERE id = CAST(:id AS uuid)")
                .setParameter("id", id.toString()).getSingleResult();
        return ((Number) v).longValue() > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Author> author(ActivitySubject s, UUID id) {
        String sql = switch (s) {
            case REQUEST -> "SELECT p.full_name, p.role FROM shipment_requests x JOIN profiles p ON p.id = x.created_by "
                    + "WHERE x.id = CAST(:id AS uuid)";
            case BID -> "SELECT p.full_name, p.role FROM bids x JOIN profiles p ON p.id = x.created_by "
                    + "WHERE x.id = CAST(:id AS uuid)";
            case INCIDENT -> "SELECT p.full_name, p.role FROM incidents x JOIN profiles p ON p.id = x.reported_by_profile_id "
                    + "WHERE x.id = CAST(:id AS uuid)";
            case TRIP -> "SELECT p.full_name, p.role FROM audit_log a JOIN profiles p ON p.id = a.actor_profile_id "
                    + "WHERE a.entity = 'carrier_trip' AND a.entity_id = CAST(:id AS uuid) AND a.action = 'TRIP_PUBLISHED' "
                    + "ORDER BY a.occurred_at LIMIT 1";
            case SHIPMENT -> null;
        };
        if (sql == null) return Optional.empty();
        List<Object[]> rows = em.createNativeQuery(sql).setParameter("id", id.toString()).getResultList();
        return rows.stream().findFirst().map(r -> new Author((String) r[0], (String) r[1]));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ActivityEntry> trail(ActivitySubject s, UUID id, int limit) {
        String bids = bidsOf(s);
        String sql = "WITH ent AS (" + entities(s) + ")\n"
                + "SELECT a.occurred_at, a.action, a.entity, a.entity_id, p.full_name, p.role, o.legal_name, "
                + "       (a.organization_id = app_org()) AS own\n"
                + "FROM audit_log a JOIN ent e ON e.entity = a.entity AND e.entity_id = a.entity_id\n"
                + "LEFT JOIN profiles p ON p.id = a.actor_profile_id\n"
                + "LEFT JOIN organizations o ON o.id = a.organization_id\n"
                + (bids == null ? "" : "UNION ALL\n"
                + "SELECT b.created_at, 'BID_PLACED', 'bid', b.id, p.full_name, p.role, o.legal_name, "
                + "       (b.carrier_org_id = app_org())\n"
                + "FROM bids b JOIN profiles p ON p.id = b.created_by LEFT JOIN organizations o ON o.id = b.carrier_org_id\n"
                + "WHERE " + bids + " AND (b.carrier_org_id = app_org() OR app_org_type() = 'OPERATOR')\n")
                + "ORDER BY 1 DESC LIMIT :lim";
        List<Object[]> rows = em.createNativeQuery(sql).setParameter("id", id.toString()).setParameter("lim", limit)
                .getResultList();
        return rows.stream().map(r -> new ActivityEntry(time(r[0]), (String) r[1], (String) r[2], (UUID) r[3],
                (String) r[4], (String) r[5], (String) r[6], Boolean.TRUE.equals(r[7]))).toList();
    }

    private static OffsetDateTime time(Object v) {
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
        if (v instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        return null;
    }
}
