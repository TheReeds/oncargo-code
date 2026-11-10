package pe.logyx.negotiation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.negotiation.application.port.out.MessageRepository;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.UUID;

/** Resuelve y autoriza el hilo (solicitud, transportista) para el usuario actual. */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class ChatThreads {

    @Inject MarketplaceQueries marketplace;
    @Inject MessageRepository messages;
    @Inject TenantContext tenant;

    /**
     * La solicitud si el usuario actual participa del hilo: el transportista solo en el suyo; la PYME dueña en
     * hilos de transportistas que ofertaron o escribieron; el operador lee cualquiera. Si no, 404.
     */
    public ShipmentRequest open(UUID requestId, UUID carrierOrgId) {
        ShipmentRequest r = marketplace.requestForParticipant(requestId);
        OrgType type = tenant.orgType();
        if (type == OrgType.CARRIER && !carrierOrgId.equals(tenant.orgId())) {
            throw BusinessException.notFound("Conversación");
        }
        if (type == OrgType.PYME) {
            boolean exists = marketplace.bidOfCarrier(r.id(), carrierOrgId).isPresent()
                    || messages.countThread(r.id(), carrierOrgId) > 0;
            if (!exists) throw BusinessException.notFound("Conversación");
        }
        return r;
    }
}
