package pe.logyx.operator.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.OperatorRequestFilter;
import pe.logyx.operator.application.port.out.OperatorSearchReader;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shipment.application.OperatorShipmentFilter;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL nativo de solo lectura: cruza solicitudes, envíos, organizaciones, perfiles y flota, que viven en módulos
 * distintos (mismo criterio que {@code NativeAuditLogReader}). Todas las tablas se leen con las políticas
 * {@code *_operator}, así que un usuario que no sea operador no obtiene nada. El término se pasa como parámetro.
 */
@ApplicationScoped
public class NativeOperatorSearchReader implements OperatorSearchReader {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject EntityManager em;

    @Override
    public Hits requests(String term, OperatorRequestFilter f, PageRequest page) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder("1 = 1");
        match(term, p, w, """
                r.code ILIKE :like OR r.customer_reference ILIKE :like OR o.legal_name ILIKE :like OR o.ruc LIKE :prefix
                OR r.cargo_description ILIKE :like OR CAST(r.id AS text) LIKE :prefix
                OR EXISTS (SELECT 1 FROM bids b WHERE b.request_id = r.id AND b.vehicle_plate ILIKE :like)
                OR EXISTS (SELECT 1 FROM organizations c WHERE c.id = r.assigned_carrier_org_id
                           AND (c.legal_name ILIKE :like OR c.ruc LIKE :prefix))""");
        if (f.status() != null) { w.append(" AND r.status = :status"); p.put("status", f.status().name()); }
        if (f.originCity() != null) { w.append(" AND r.origin_city = :origin"); p.put("origin", f.originCity().name()); }
        if (f.destinationCity() != null) { w.append(" AND r.destination_city = :dest"); p.put("dest", f.destinationCity().name()); }
        if (f.fromDate() != null) {
            w.append(" AND r.created_at >= :from");
            p.put("from", f.fromDate().atStartOfDay(LIMA).toOffsetDateTime());
        }
        if (f.toDate() != null) {
            w.append(" AND r.created_at < :to");
            p.put("to", f.toDate().plusDays(1).atStartOfDay(LIMA).toOffsetDateTime());
        }
        return run("FROM shipment_requests r JOIN organizations o ON o.id = r.requester_org_id", w, p,
                "r.id", "r.created_at DESC, r.id DESC", page);
    }

    @Override
    public Hits shipments(String term, OperatorShipmentFilter f, PageRequest page) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder("1 = 1");
        match(term, p, w, """
                r.code ILIKE :like OR r.customer_reference ILIKE :like OR rq.legal_name ILIKE :like OR rq.ruc LIKE :prefix
                OR cr.legal_name ILIKE :like OR cr.ruc LIKE :prefix OR s.vehicle_plate ILIKE :like
                OR s.driver_name ILIKE :like OR CAST(s.id AS text) LIKE :prefix OR CAST(s.request_id AS text) LIKE :prefix""");
        if (f.status() != null) { w.append(" AND s.status = :status"); p.put("status", f.status().name()); }
        if (f.originCity() != null) { w.append(" AND r.origin_city = :origin"); p.put("origin", f.originCity().name()); }
        if (f.destinationCity() != null) { w.append(" AND r.destination_city = :dest"); p.put("dest", f.destinationCity().name()); }
        if (f.fromDate() != null) {
            w.append(" AND s.created_at >= :from");
            p.put("from", f.fromDate().atStartOfDay(LIMA).toOffsetDateTime());
        }
        if (f.toDate() != null) {
            w.append(" AND s.created_at < :to");
            p.put("to", f.toDate().plusDays(1).atStartOfDay(LIMA).toOffsetDateTime());
        }
        return run("""
                FROM shipments s JOIN shipment_requests r ON r.id = s.request_id
                JOIN organizations rq ON rq.id = s.requester_org_id JOIN organizations cr ON cr.id = s.carrier_org_id""",
                w, p, "s.id", "s.created_at DESC, s.id DESC", page);
    }

    @Override
    public Hits organizations(String term, Organization.VerificationStatus status, OrgType type, PageRequest page) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder("o.type <> 'OPERATOR'");
        match(term, p, w, """
                o.legal_name ILIKE :like OR o.ruc LIKE :prefix OR o.city ILIKE :like OR CAST(o.id AS text) LIKE :prefix
                OR EXISTS (SELECT 1 FROM profiles u WHERE u.organization_id = o.id AND u.email ILIKE :like)
                OR EXISTS (SELECT 1 FROM vehicles v WHERE v.organization_id = o.id AND v.plate ILIKE :like)""");
        if (status != null) { w.append(" AND o.verification_status = :vstatus"); p.put("vstatus", status.name()); }
        if (type != null) { w.append(" AND o.type = :otype"); p.put("otype", type.name()); }
        // Misma ordenación que la bandeja de verificación: las más antiguas primero.
        return run("FROM organizations o", w, p, "o.id", "o.created_at ASC, o.id ASC", page);
    }

    /** Agrega el bloque de coincidencias de texto: {@code :like} (contiene) y {@code :prefix} (RUC/id: empieza por). */
    private static void match(String term, Map<String, Object> p, StringBuilder where, String clauses) {
        String t = term.replace("%", "").replace("_", "").replace("\\", "").trim();
        p.put("like", "%" + t + "%");
        p.put("prefix", t.toLowerCase() + "%");
        where.append(" AND (").append(clauses).append(")");
    }

    @SuppressWarnings("unchecked")
    private Hits run(String from, StringBuilder where, Map<String, Object> params, String idExpr, String order,
                     PageRequest page) {
        Query count = em.createNativeQuery("SELECT count(*) " + from + " WHERE " + where);
        Query ids = em.createNativeQuery("SELECT " + idExpr + " " + from + " WHERE " + where + " ORDER BY " + order
                + " LIMIT " + page.size() + " OFFSET " + page.offset());
        params.forEach((k, v) -> { count.setParameter(k, v); ids.setParameter(k, v); });
        long total = ((Number) count.getSingleResult()).longValue();
        List<Object> rows = ids.getResultList();
        return new Hits(rows.stream().map(o -> o instanceof UUID u ? u : UUID.fromString(o.toString())).toList(), total);
    }
}
