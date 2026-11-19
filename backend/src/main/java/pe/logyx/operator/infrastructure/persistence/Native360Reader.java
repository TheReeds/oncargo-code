package pe.logyx.operator.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.operator.application.port.out.Organization360Reader;

import java.util.List;
import java.util.UUID;

/** Ficha 360 (2.15): conteos con SQL nativo bajo las políticas {@code *_operator}; nunca SystemTx. */
@ApplicationScoped
public class Native360Reader implements Organization360Reader {

    @Inject EntityManager em;

    @Override
    public Counts counts(UUID org) {
        Object[] r = (Object[]) em.createNativeQuery("""
                SELECT
                  (SELECT count(*) FROM vehicles v WHERE v.organization_id = ?1 AND v.archived_at IS NULL),
                  (SELECT count(*) FROM vehicles v WHERE v.organization_id = ?1 AND v.archived_at IS NOT NULL),
                  (SELECT count(*) FROM drivers d WHERE d.organization_id = ?1),
                  (SELECT count(*) FROM shipments s WHERE (s.requester_org_id = ?1 OR s.carrier_org_id = ?1) AND s.status = 'PENDING'),
                  (SELECT count(*) FROM shipments s WHERE (s.requester_org_id = ?1 OR s.carrier_org_id = ?1) AND s.status = 'IN_TRANSIT'),
                  (SELECT count(*) FROM shipments s WHERE (s.requester_org_id = ?1 OR s.carrier_org_id = ?1) AND s.status = 'DELIVERED'),
                  (SELECT count(*) FROM shipments s WHERE (s.requester_org_id = ?1 OR s.carrier_org_id = ?1) AND s.status = 'CANCELLED'),
                  (SELECT count(*) FROM shipments s WHERE s.cancelled_by_org_id = ?1),
                  (SELECT count(*) FROM bids b WHERE b.carrier_org_id = ?1),
                  (SELECT count(*) FROM bids b WHERE b.carrier_org_id = ?1 AND b.status = 'WITHDRAWN'),
                  (SELECT count(*) FROM bids b WHERE b.carrier_org_id = ?1 AND b.status = 'ACCEPTED'),
                  (SELECT count(*) FROM shipment_requests r WHERE r.requester_org_id = ?1),
                  (SELECT count(*) FROM shipment_requests r WHERE r.requester_org_id = ?1 AND r.status = 'CANCELLED'),
                  (SELECT count(*) FROM incidents i WHERE i.requester_org_id = ?1 OR i.carrier_org_id = ?1),
                  (SELECT count(*) FROM incidents i WHERE (i.requester_org_id = ?1 OR i.carrier_org_id = ?1)
                                                       AND i.status IN ('OPEN', 'IN_REVIEW')),
                  (SELECT count(*) FROM incidents i WHERE i.reported_by_org_id = ?1)
                """).setParameter(1, org).getSingleResult();
        long[] n = new long[r.length];
        for (int i = 0; i < r.length; i++) n[i] = r[i] == null ? 0 : ((Number) r[i]).longValue();
        return new Counts(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], n[9], n[10], n[11], n[12], n[13], n[14],
                n[15]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<VehicleRow> vehicles(UUID org) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, plate, type, status, archived_at IS NOT NULL FROM vehicles
                 WHERE organization_id = ?1 ORDER BY archived_at NULLS FIRST, plate LIMIT 100""")
                .setParameter(1, org).getResultList();
        return rows.stream().map(r -> new VehicleRow((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                Boolean.TRUE.equals(r[4]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UUID> recentShipments(UUID org, int limit) {
        return em.createNativeQuery("""
                SELECT id FROM shipments WHERE requester_org_id = ?1 OR carrier_org_id = ?1
                 ORDER BY created_at DESC LIMIT ?2""", UUID.class).setParameter(1, org).setParameter(2, limit)
                .getResultList();
    }
}
