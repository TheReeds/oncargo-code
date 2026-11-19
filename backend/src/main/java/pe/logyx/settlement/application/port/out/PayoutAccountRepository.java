package pe.logyx.settlement.application.port.out;

import pe.logyx.settlement.domain.model.PayoutAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datos de cobro del transportista (V24). El número entra y sale en claro; el adaptador lo cifra. */
public interface PayoutAccountRepository {
    List<PayoutAccount> activeOf(UUID carrierOrgId);

    Optional<PayoutAccount> find(UUID id);

    void insert(PayoutAccount a, UUID createdBy);

    void archive(UUID id);

    /** Deja a {@code id} como principal y quita la marca a las demás de la empresa. */
    void makePrimary(UUID carrierOrgId, UUID id);
}
