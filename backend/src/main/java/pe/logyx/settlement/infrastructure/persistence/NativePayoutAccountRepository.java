package pe.logyx.settlement.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.settlement.application.port.out.PayoutAccountRepository;
import pe.logyx.settlement.application.port.out.PayoutCipher;
import pe.logyx.settlement.domain.model.PayoutAccount;
import pe.logyx.settlement.domain.model.PayoutKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static pe.logyx.settlement.infrastructure.persistence.NativeSettlementRepository.odt;
import static pe.logyx.settlement.infrastructure.persistence.NativeSettlementRepository.uuid;

/** {@code carrier_payout_accounts} bajo RLS; el número se cifra con {@link PayoutCipher} antes de llegar a la BD. */
@ApplicationScoped
public class NativePayoutAccountRepository implements PayoutAccountRepository {

    private static final String SELECT = """
            SELECT id, carrier_org_id, kind, bank_name, holder_name, number_enc, number_last4, is_primary, archived_at,
                   created_at FROM carrier_payout_accounts
            """;

    @Inject EntityManager em;
    @Inject PayoutCipher cipher;

    @Override
    @SuppressWarnings("unchecked")
    public List<PayoutAccount> activeOf(UUID carrierOrgId) {
        List<Object[]> rows = em.createNativeQuery(SELECT
                        + " WHERE carrier_org_id = ?1 AND archived_at IS NULL ORDER BY is_primary DESC, created_at")
                .setParameter(1, carrierOrgId).getResultList();
        return rows.stream().map(this::map).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<PayoutAccount> find(UUID id) {
        List<Object[]> rows = em.createNativeQuery(SELECT + " WHERE id = ?1").setParameter(1, id).getResultList();
        return rows.stream().findFirst().map(this::map);
    }

    @Override
    public void insert(PayoutAccount a, UUID createdBy) {
        em.createNativeQuery("""
                INSERT INTO carrier_payout_accounts (id, carrier_org_id, kind, bank_name, holder_name, number_enc,
                                                     number_last4, is_primary, created_by)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
                """).setParameter(1, a.id()).setParameter(2, a.carrierOrgId()).setParameter(3, a.kind().name())
                .setParameter(4, a.bankName()).setParameter(5, a.holderName())
                .setParameter(6, cipher.encrypt(a.number())).setParameter(7, a.last4())
                .setParameter(8, a.primary()).setParameter(9, createdBy).executeUpdate();
    }

    @Override
    public void archive(UUID id) {
        em.createNativeQuery("UPDATE carrier_payout_accounts SET archived_at = now(), is_primary = false, "
                + "updated_at = now() WHERE id = ?1 AND archived_at IS NULL").setParameter(1, id).executeUpdate();
    }

    @Override
    public void makePrimary(UUID carrierOrgId, UUID id) {
        em.createNativeQuery("UPDATE carrier_payout_accounts SET is_primary = (id = ?2), updated_at = now() "
                        + "WHERE carrier_org_id = ?1 AND archived_at IS NULL")
                .setParameter(1, carrierOrgId).setParameter(2, id).executeUpdate();
    }

    private PayoutAccount map(Object[] r) {
        return new PayoutAccount(uuid(r[0]), uuid(r[1]), PayoutKind.valueOf((String) r[2]), (String) r[3],
                (String) r[4], cipher.decrypt((byte[]) r[5]), (String) r[6], (Boolean) r[7], odt(r[8]), odt(r[9]));
    }
}
