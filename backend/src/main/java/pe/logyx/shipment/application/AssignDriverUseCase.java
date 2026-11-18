package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.DriverAccount;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.event.DriverAssigned;
import pe.logyx.shipment.application.event.ShipmentReassigned;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.model.Shipment;

import jakarta.enterprise.event.Event;

import java.util.UUID;

/**
 * El transportista asigna uno de sus conductores al envío (RF66), o lo releva ya en ruta con motivo (F06).
 * El conductor no puede tener otro envío activo que se solape (F11, LOGYX-SHP-004).
 */
@ApplicationScoped
public class AssignDriverUseCase {

    @Inject ShipmentRepository shipments;
    @Inject FleetService fleet;
    @Inject ShipmentQueries queries;
    @Inject ResourceScheduler schedule;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<DriverAssigned> driverAssigned;
    @Inject Event<ShipmentReassigned> reassigned;

    @Transactional
    public ShipmentSummary assign(UUID shipmentId, UUID driverId) {
        return assign(shipmentId, driverId, null);
    }

    @Transactional
    public ShipmentSummary assign(UUID shipmentId, UUID driverId, String reason) {
        Shipment s = shipments.findForCarrier(shipmentId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Envío"));
        String why = ContactInfoPolicy.clean(reason, "reason");                                          // RN07
        s.ensureReassignable(why);
        DriverAccount d = fleet.ownDriver(driverId);
        if (!d.active()) {                                                                                 // F31
            throw BusinessException.conflict("LOGYX-FLT-003",
                    "La cuenta de este conductor está desactivada: reactívala o elige otro conductor", "driverId");
        }
        UUID previousDriverId = s.driverId();
        boolean changed = previousDriverId != null && !previousDriverId.equals(d.driver().id());
        UUID previous = !changed ? null
                : fleet.drivers().stream().filter(x -> x.driver().id().equals(previousDriverId))
                        .map(x -> x.driver().profileId()).findFirst().orElse(null);
        schedule.ensureDriverFree(d.driver().id(), s.vehicleId(), schedule.windowOf(s), s.loadGroupId(), s.id()); // F11
        boolean inRoute = s.status() == Shipment.Status.IN_TRANSIT;
        s.assignDriver(d.driver().id(), d.fullName(), why);
        shipments.save(s);
        if (inRoute && changed) fleet.swapDriverInRoute(previousDriverId, d.driver().id());
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_ASSIGNED", "shipment", s.id(),
                d.driver().id() + (why == null ? "" : " motivo=" + why));
        // Tiempo real (ADR-0007): el conductor ve su envío nuevo sin recargar; el anterior deja de verlo.
        driverAssigned.fire(new DriverAssigned(s.id(), s.requesterOrgId(), s.carrierOrgId(), d.driver().profileId(), previous));
        ShipmentSummary result = queries.detail(s.id());
        if (changed) {
            reassigned.fire(new ShipmentReassigned(s.id(), s.requesterOrgId(), s.carrierOrgId(), "DRIVER",
                    s.vehiclePlate(), d.fullName(), why, inRoute, result.routeLabel()));
        }
        return result;
    }
}
