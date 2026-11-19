package pe.logyx.settlement.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.settlement.application.port.out.CommissionCollectionRepository;
import pe.logyx.settlement.domain.model.PaymentMethod;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.List;
import java.util.UUID;

import static pe.logyx.settlement.infrastructure.persistence.NativeSettlementRepository.*;

/** {@code commission_collections} bajo RLS (transportista: las suyas; operador: todas). */
@ApplicationScoped
public class NativeCommissionCollectionRepository implements CommissionCollectionRepository {

    @Inject EntityManager em;

    @Override
    public void insert(Collection c, UUID recordedByOrgId) {
        em.createNativeQuery("""
                INSERT INTO commission_collections (id, carrier_org_id, amount, method, operation_number, paid_on, note,
                                                    shipments_count, recorded_by, recorded_by_org_id)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
                """).setParameter(1, c.id()).setParameter(2, c.carrierOrgId()).setParameter(3, c.amount())
                .setParameter(4, c.method().name()).setParameter(5, c.operationNumber()).setParameter(6, c.paidOn())
                .setParameter(7, c.note()).setParameter(8, c.shipmentsCount()).setParameter(9, c.recordedBy())
                .setParameter(10, recordedByOrgId).executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Paged<Collection> list(UUID carrierOrgId, PageRequest page) {
        String where = carrierOrgId == null ? "" : " WHERE c.carrier_org_id = ?1";
        var count = em.createNativeQuery("SELECT count(*) FROM commission_collections c" + where);
        var q = em.createNativeQuery("""
                SELECT c.id, c.carrier_org_id, o.legal_name, c.amount, c.method, c.operation_number, c.paid_on, c.note,
                       c.shipments_count, c.recorded_by, p.full_name, c.created_at
                  FROM commission_collections c
                  JOIN organizations o ON o.id = c.carrier_org_id
                  LEFT JOIN profiles p ON p.id = c.recorded_by
                """ + where + " ORDER BY c.created_at DESC LIMIT " + page.size() + " OFFSET " + page.offset());
        if (carrierOrgId != null) {
            count.setParameter(1, carrierOrgId);
            q.setParameter(1, carrierOrgId);
        }
        long total = ((Number) count.getSingleResult()).longValue();
        List<Object[]> rows = q.getResultList();
        return new Paged<>(rows.stream().map(r -> new Collection(uuid(r[0]), uuid(r[1]), (String) r[2], dec(r[3]),
                PaymentMethod.valueOf((String) r[4]), (String) r[5], date(r[6]), (String) r[7],
                ((Number) r[8]).intValue(), uuid(r[9]), (String) r[10], odt(r[11]))).toList(), page, total);
    }
}
