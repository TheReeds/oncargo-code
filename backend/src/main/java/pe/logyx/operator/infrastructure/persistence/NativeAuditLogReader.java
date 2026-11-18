package pe.logyx.operator.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.logyx.operator.application.AuditFilter;
import pe.logyx.operator.application.AuditRecord;
import pe.logyx.operator.application.port.out.AuditLogReader;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL nativo: {@code audit_log} no tiene asociaciones JPA hacia perfiles ni organizaciones (y viven en otros módulos).
 * Los LEFT JOIN respetan RLS: el operador lee {@code profiles} y {@code organizations} completos (V2).
 */
@ApplicationScoped
public class NativeAuditLogReader implements AuditLogReader {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject EntityManager em;

    @Override
    public Paged<AuditRecord> search(AuditFilter f, PageRequest page) {
        Map<String, Object> params = new HashMap<>();
        String where = where(f, params);
        Query count = em.createNativeQuery("SELECT count(*) FROM audit_log a WHERE " + where);
        bind(count, params);
        long total = ((Number) count.getSingleResult()).longValue();

        Query q = em.createNativeQuery("""
                SELECT a.id, a.occurred_at, a.actor_profile_id, p.email, p.full_name,
                       a.organization_id, o.legal_name, o.type, a.action, a.entity, a.entity_id, a.detail
                FROM audit_log a
                LEFT JOIN profiles p ON p.id = a.actor_profile_id
                LEFT JOIN organizations o ON o.id = a.organization_id
                WHERE\s""" + where + " ORDER BY a.occurred_at DESC, a.id DESC");
        bind(q, params);
        q.setFirstResult(page.offset()).setMaxResults(page.size());
        @SuppressWarnings("unchecked") List<Object[]> rows = q.getResultList();
        List<AuditRecord> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new AuditRecord(((Number) r[0]).longValue(), time(r[1]), uuid(r[2]), (String) r[3], (String) r[4],
                    uuid(r[5]), (String) r[6], r[7] == null ? null : OrgType.valueOf((String) r[7]),
                    (String) r[8], (String) r[9], uuid(r[10]), (String) r[11]));
        }
        return new Paged<>(out, page, total);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> distinctActions() {
        return em.createNativeQuery("SELECT DISTINCT action FROM audit_log ORDER BY action").getResultList();
    }

    private static String where(AuditFilter f, Map<String, Object> p) {
        StringBuilder w = new StringBuilder("1 = 1");
        if (f.actorId() != null) { w.append(" AND a.actor_profile_id = :actorId"); p.put("actorId", f.actorId()); }
        if (f.organizationId() != null) { w.append(" AND a.organization_id = :orgId"); p.put("orgId", f.organizationId()); }
        if (!f.actions().isEmpty()) { w.append(" AND a.action IN (:actions)"); p.put("actions", f.actions()); }
        if (f.entity() != null) { w.append(" AND a.entity = :entity"); p.put("entity", f.entity()); }
        if (f.entityId() != null) { w.append(" AND a.entity_id = :entityId"); p.put("entityId", f.entityId()); }
        if (f.from() != null) {
            w.append(" AND a.occurred_at >= :from");
            p.put("from", f.from().atStartOfDay(LIMA).toOffsetDateTime());
        }
        if (f.to() != null) {
            w.append(" AND a.occurred_at < :to");
            p.put("to", f.to().plusDays(1).atStartOfDay(LIMA).toOffsetDateTime());
        }
        return w.toString();
    }

    private static void bind(Query q, Map<String, Object> params) {
        params.forEach(q::setParameter);
    }

    private static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static OffsetDateTime time(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime t) return t;
        if (o instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
        if (o instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if (o instanceof java.time.LocalDateTime l) return l.atOffset(ZoneOffset.UTC);
        throw new IllegalStateException("Tipo de fecha inesperado: " + o.getClass());
    }
}
