package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.DriverAccount;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.event.DriverAssigned;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import jakarta.enterprise.event.Event;

import java.util.UUID;

/** El transportista asigna uno de sus conductores al envío. */
@ApplicationScoped
public class AssignDriverUseCase {

    @Inject ShipmentRepository shipments;
    @Inject FleetService fleet;
    @Inject ShipmentQueries queries;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<DriverAssigned> driverAssigned;

    @Transactional
    public ShipmentSummary assign(UUID shipmentId, UUID driverId) {
        Shipment s = shipments.findForCarrier(shipmentId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Envío"));
        s.ensureDriverAssignable();
        DriverAccount d = fleet.ownDriver(driverId);
        UUID previous = s.driverId() == null || s.driverId().equals(d.driver().id()) ? null
                : fleet.drivers().stream().filter(x -> x.driver().id().equals(s.driverId()))
                        .map(x -> x.driver().profileId()).findFirst().orElse(null);
        s.assignDriver(d.driver().id(), d.fullName());
        shipments.save(s);
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_ASSIGNED", "shipment", s.id(),
                d.driver().id().toString());
        // Tiempo real (ADR-0007): el conductor ve su envío nuevo sin recargar; el anterior deja de verlo.
        driverAssigned.fire(new DriverAssigned(s.id(), s.requesterOrgId(), s.carrierOrgId(), d.driver().profileId(), previous));
        return queries.detail(s.id());
    }
}
