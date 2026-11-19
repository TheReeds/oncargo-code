package pe.logyx.search.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.logyx.catalog.domain.City;
import pe.logyx.search.application.port.out.SearchReader;
import pe.logyx.search.domain.SearchQuery;
import pe.logyx.shared.tenancy.OrgType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL nativo de solo lectura que cruza solicitudes, envíos, ofertas y organizaciones (un read model de búsqueda, como
 * {@code NativeAuditLogReader}). Corre en la transacción del usuario con el datasource de la aplicación: <b>la RLS
 * decide qué filas existen</b> para él; los filtros por organización de aquí son una segunda barrera, no la primera.
 * Nunca por {@code SystemTx}. Todo valor del usuario va como parámetro (los comodines LIKE ya vienen escapados).
 */
@ApplicationScoped
public class NativeSearchReader implements SearchReader {

    private static final String ESC = " escape '\\'";

    @Inject EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<RequestRow> requests(SearchQuery q, UUID org, OrgType viewer, int limit) {
        List<String> ors = new ArrayList<>();
        ors.add("lower(r.code) like :contains" + ESC);
        ors.add("lower(r.cargo_description) like :contains" + ESC);
        if (q.code() != null) ors.add("r.code = :code");
        // La referencia propia es privada: PYME dueña y operador; un transportista solo la del envío que ganó.
        if (viewer == OrgType.CARRIER) {
            ors.add("(r.assigned_carrier_org_id = :org and lower(r.customer_reference) like :contains" + ESC + ")");
        } else {
            ors.add("lower(r.customer_reference) like :contains" + ESC);
        }
        routeClause(q, "r.origin_city", "r.destination_city", ors);
        String sql = "SELECT r.id, r.code, r.customer_reference, r.origin_city, r.destination_city, r.status, "
                + "r.pickup_date, " + (viewer == OrgType.CARRIER
                ? "EXISTS (SELECT 1 FROM bids b WHERE b.request_id = r.id AND b.carrier_org_id = :org)" : "FALSE")
                + " FROM shipment_requests r WHERE (" + String.join(" OR ", ors) + ") "
                + (viewer == OrgType.PYME ? "AND r.requester_org_id = :org " : "")
                + "ORDER BY (CASE WHEN r.code = :codeOrNone THEN 0 ELSE 1 END), r.created_at DESC, r.id";
        Query query = em.createNativeQuery(sql).setParameter("contains", q.contains())
                .setParameter("codeOrNone", q.code() == null ? "" : q.code());
        bind(query, sql, q, org);
        List<Object[]> rows = query.setMaxResults(limit).getResultList();
        return rows.stream().map(r -> new RequestRow((UUID) r[0], (String) r[1], (String) r[2], City.valueOf((String) r[3]),
                City.valueOf((String) r[4]), (String) r[5], date(r[6]), Boolean.TRUE.equals(r[7]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ShipmentRow> shipments(SearchQuery q, UUID org, OrgType viewer, int limit) {
        List<String> ors = new ArrayList<>();
        ors.add("lower(r.code) like :contains" + ESC);
        ors.add("lower(r.customer_reference) like :contains" + ESC);
        ors.add("lower(s.vehicle_plate) like :contains" + ESC);
        ors.add("lower(s.driver_name) like :contains" + ESC);
        if (q.code() != null) ors.add("r.code = :code");
        routeClause(q, "r.origin_city", "r.destination_city", ors);
        String counterpart = switch (viewer) {
            case PYME -> "oc.legal_name";
            case CARRIER -> "oq.legal_name";
            case OPERATOR -> "oq.legal_name || ' → ' || oc.legal_name";
        };
        String sql = "SELECT s.id, r.code, r.customer_reference, r.origin_city, r.destination_city, s.status, "
                + "s.vehicle_plate, " + counterpart + " FROM shipments s JOIN shipment_requests r ON r.id = s.request_id "
                + "JOIN organizations oq ON oq.id = s.requester_org_id JOIN organizations oc ON oc.id = s.carrier_org_id "
                + "WHERE (" + String.join(" OR ", ors) + ") "
                + (viewer == OrgType.OPERATOR ? "" : "AND (s.requester_org_id = :org OR s.carrier_org_id = :org) ")
                + "ORDER BY (CASE WHEN r.code = :codeOrNone THEN 0 ELSE 1 END), s.created_at DESC, s.id";
        Query query = em.createNativeQuery(sql).setParameter("contains", q.contains())
                .setParameter("codeOrNone", q.code() == null ? "" : q.code());
        bind(query, sql, q, org);
        List<Object[]> rows = query.setMaxResults(limit).getResultList();
        return rows.stream().map(r -> new ShipmentRow((UUID) r[0], (String) r[1], (String) r[2], City.valueOf((String) r[3]),
                City.valueOf((String) r[4]), (String) r[5], (String) r[6], (String) r[7])).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CounterpartRow> counterparts(SearchQuery q, UUID org, OrgType viewer, int limit) {
        String where;
        switch (viewer) {
            // Solo contrapartes con una relación real (envío u oferta compartidos): no es un directorio.
            case PYME -> where = "o.type = 'CARRIER' AND (o.id IN (SELECT carrier_org_id FROM shipments WHERE requester_org_id = :org) "
                    + "OR o.id IN (SELECT carrier_org_id FROM bids WHERE requester_org_id = :org))";
            case CARRIER -> where = "o.type = 'PYME' AND (o.id IN (SELECT requester_org_id FROM shipments WHERE carrier_org_id = :org) "
                    + "OR o.id IN (SELECT requester_org_id FROM bids WHERE carrier_org_id = :org))";
            default -> where = "o.type <> 'OPERATOR'";
        }
        String match = "lower(o.legal_name) like :contains" + ESC
                + (viewer == OrgType.OPERATOR ? " OR o.ruc like :prefix" + ESC : "");
        String sql = "SELECT o.id, o.legal_name, o.type, o.city FROM organizations o WHERE " + where + " AND (" + match
                + ") ORDER BY o.legal_name, o.id";
        Query query = em.createNativeQuery(sql).setParameter("contains", q.contains());
        if (viewer == OrgType.OPERATOR) query.setParameter("prefix", q.prefix());
        else query.setParameter("org", org);
        List<Object[]> rows = query.setMaxResults(limit).getResultList();
        return rows.stream().map(r -> new CounterpartRow((UUID) r[0], (String) r[1], OrgType.valueOf((String) r[2]),
                (String) r[3])).toList();
    }

    /** "lima juliaca" → ruta entre ambas; "lima" → origen o destino. */
    private static void routeClause(SearchQuery q, String origin, String destination, List<String> ors) {
        if (q.cities().isEmpty()) return;
        ors.add(q.isRoute()
                ? "(" + origin + " IN (:cities) AND " + destination + " IN (:cities))"
                : "(" + origin + " IN (:cities) OR " + destination + " IN (:cities))");
    }

    private static void bind(Query query, String sql, SearchQuery q, UUID org) {
        if (q.code() != null) query.setParameter("code", q.code());
        if (!q.cities().isEmpty()) query.setParameter("cities", q.cityNames());
        if (sql.contains(":org")) query.setParameter("org", org);
    }

    private static LocalDate date(Object value) {
        if (value == null) return null;
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
}
