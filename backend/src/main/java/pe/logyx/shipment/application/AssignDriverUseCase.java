package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.DriverAccount;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import java.util.UUID;

/** El transportista asigna uno de sus conductores al envío. */
@ApplicationScoped
public class AssignDriverUseCase {

    @Inject ShipmentRepository shipments;
    @Inject FleetService fleet;
    @Inject ShipmentQueries queries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @Transactional
    public ShipmentSummary assign(UUID shipmentId, UUID driverId) {
        Shipment s = shipments.findForCarrier(shipmentId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Envío"));
        s.ensureDriverAssignable();
        DriverAccount d = fleet.ownDriver(driverId);
        s.assignDriver(d.driver().id(), d.fullName());
        shipments.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_ASSIGNED", "shipment", s.id(),
                d.driver().id().toString());
        return queries.detail(s.id());
    }
}
