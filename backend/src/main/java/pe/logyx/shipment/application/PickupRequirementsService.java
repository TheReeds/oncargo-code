package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import java.util.UUID;

/** La PYME dueña del envío fija qué pide el recojo (roadmap 2.5): foto de la carga y peso medido. */
@ApplicationScoped
public class PickupRequirementsService {

    @Inject ShipmentQueries queries;
    @Inject ShipmentRepository shipments;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @Transactional
    public ShipmentSummary change(UUID shipmentId, boolean photo, boolean weight) {
        Shipment s = queries.detail(shipmentId).shipment();
        if (tenant.orgType() != OrgType.PYME || !s.requesterOrgId().equals(tenant.orgId())) {
            throw BusinessException.notFound("Envío");
        }
        var before = s.pickupRequirements();
        s.changePickupRequirements(new Shipment.PickupRequirements(photo, weight));
        shipments.save(s);
        if (!before.equals(s.pickupRequirements())) {
            audit.record(tenant.profileId(), tenant.orgId(), "PICKUP_REQUIREMENTS_CHANGED", "shipment", s.id(),
                    "foto=" + photo + " peso=" + weight);
        }
        return queries.detail(s.id());
    }
}
