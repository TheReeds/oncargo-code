package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.event.CarriersInvited;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.PreferenceKind;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.identity.application.OrganizationDirectory;

import java.util.List;
import java.util.UUID;

/** «Invitar a ofertar» (roadmap 2.9): avisa a los transportistas preferidos de la PYME que hay una carga para ellos. */
@ApplicationScoped
public class InviteCarriersUseCase {

    @Inject CarrierPreferenceService preferences;
    @Inject ShipmentRequestRepository requests;
    @Inject OrganizationDirectory organizations;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<CarriersInvited> invited;

    /** Sobre una solicitud ya publicada de la PYME. Devuelve a cuántos transportistas se avisó. */
    @Transactional
    public int invitePreferred(UUID requestId) {
        ShipmentRequest r = requests.findOwned(requestId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (!r.isPublished()) {
            throw BusinessException.conflict("LOGYX-REQ-008", "Solo se invita a ofertar en una solicitud publicada");
        }
        return invite(r);
    }

    /** Para quien ya tiene la solicitud (publicar con invitación): dentro de la transacción en curso. */
    @Transactional(Transactional.TxType.MANDATORY)
    public int invite(ShipmentRequest r) {
        List<UUID> carriers = List.copyOf(preferences.ids(PreferenceKind.PREFERRED));
        if (carriers.isEmpty()) return 0;
        String name = organizations.get(r.requesterOrgId()).legalName();
        invited.fire(new CarriersInvited(r.id(), r.requesterOrgId(), name, r.routeLabel(), r.cargo().pickupDate(),
                r.cargo().weightKg(), carriers));
        audit.record(tenant.profileId(), tenant.orgId(), "CARRIERS_INVITED", "shipment_request", r.id(),
                "transportistas=" + carriers.size());
        return carriers.size();
    }
}
