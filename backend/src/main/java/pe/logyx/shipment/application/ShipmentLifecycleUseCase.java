package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.event.ShipmentReassigned;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.application.port.out.IncidentHoldProvider;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Salidas de emergencia de un envío adjudicado (F06) y estado final fijado por el operador (RF55, F13):
 * <ul>
 *   <li><b>Cancelar</b> con motivo: la PYME o el transportista ("devolver") antes del recojo; el operador también en
 *       tránsito. Una incidencia activa impide que las partes cancelen (RN11: la resuelve el operador). La
 *       solicitud queda CANCELLED (marketplace, por el outbox) y la PYME puede republicarla.</li>
 *   <li><b>Cambiar vehículo</b> (transportista): antes del recojo o en ruta con motivo; el vehículo nuevo debe ser
 *       propio, disponible, apto para la carga (RN02: kg, tipo, m³) y sin otro envío solapado (F11).</li>
 *   <li><b>Dar por entregado</b> (operador, al resolver una incidencia): cierra las paradas y libera la flota.</li>
 * </ul>
 * La contraparte se entera por el outbox ({@code ShipmentStatusChanged} / {@code ShipmentReassigned}).
 */
@ApplicationScoped
public class ShipmentLifecycleUseCase {

    @Inject ShipmentRepository shipments;
    @Inject ShipmentStopRepository stops;
    @Inject ShipmentQueries queries;
    @Inject RequestInfoProvider requests;
    @Inject IncidentHoldProvider holds;
    @Inject ResourceScheduler schedule;
    @Inject FleetService fleet;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<ShipmentStatusChanged> statusChanged;
    @Inject Event<ShipmentReassigned> reassigned;

    /** PYME/transportista: antes del recojo. Operador: también en tránsito. Motivo obligatorio. */
    @Transactional
    public ShipmentSummary cancel(UUID shipmentId, String reason) {
        boolean operator = tenant.orgType() == OrgType.OPERATOR;
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> BusinessException.notFound("Envío"));
        if (!operator && (!s.isParty(tenant.orgId()) || tenant.isDriver())) throw BusinessException.notFound("Envío");
        if (!operator && holds.hasActiveIncident(s.id())) {                                              // RN11
            throw BusinessException.conflict("LOGYX-TRK-005",
                    "El envío tiene una incidencia activa: solo el operador puede cancelarlo al resolverla (RN11)");
        }
        String why = operator ? reason : ContactInfoPolicy.clean(reason, "reason");                       // RN07
        return doCancel(s, why, operator, "SHIPMENT_CANCELLED");
    }

    /** Operador (RF55): cancela al resolver una incidencia (carga extraviada, servicio que no se presta). */
    @Transactional(Transactional.TxType.MANDATORY)
    public ShipmentSummary cancelByOperator(UUID shipmentId, String reason) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> BusinessException.notFound("Envío"));
        return doCancel(s, reason, true, "SHIPMENT_CANCELLED_BY_INCIDENT");
    }

    /** Operador (RF55): da por entregado un envío en tránsito al resolver una incidencia. */
    @Transactional(Transactional.TxType.MANDATORY)
    public ShipmentSummary markDeliveredByOperator(UUID shipmentId, String reason) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> BusinessException.notFound("Envío"));
        OffsetDateTime now = OffsetDateTime.now();
        s.markDeliveredByOperator(now);
        shipments.save(s);
        for (ShipmentStop stop : stops.listByShipment(s.id())) {
            if (!stop.isCompleted()) { stop.forceComplete(now); stops.save(stop); }
        }
        fleet.finishTrip(s.vehicleId(), s.driverId());
        audit.record(tenant.profileId(), tenant.orgId(), "SHIPMENT_DELIVERED_BY_OPERATOR", "shipment", s.id(), reason);
        ShipmentSummary result = queries.detail(s.id());
        statusChanged.fire(new ShipmentStatusChanged(s.id(), s.requesterOrgId(), s.carrierOrgId(), s.status(),
                result.routeLabel(), tenant.orgId(), reason, s.requestId()));
        return result;
    }

    /** Transportista: cambia el vehículo del envío (F06). */
    @Transactional
    public ShipmentSummary changeVehicle(UUID shipmentId, UUID vehicleId, String reason) {
        Shipment s = shipments.findForCarrier(shipmentId, tenant.orgId())
                .orElseThrow(() -> BusinessException.notFound("Envío"));
        String why = ContactInfoPolicy.clean(reason, "reason");                                          // RN07
        s.ensureReassignable(why);
        if (vehicleId.equals(s.vehicleId())) {
            throw BusinessException.badRequest("LOGYX-SHP-007", "El envío ya usa ese vehículo", "vehicleId");
        }
        Vehicle v = fleet.ownVehicle(vehicleId);
        var info = requests.byIds(List.of(s.requestId())).get(s.requestId());
        if (info != null) {
            VehicleCargoCompatibility.ensureSuitable(v, info.cargoType(), info.weightKg(), info.volumeM3());  // RN02
        } else if (!v.isAvailable()) {
            throw BusinessException.conflict("LOGYX-CAP-002", "El vehículo no está disponible", "vehicleId");
        }
        schedule.ensureVehicleFree(v.id(), schedule.windowOf(s), s.loadGroupId(), s.id());            // F11
        if (s.driverId() != null) {
            schedule.ensureDriverFree(s.driverId(), v.id(), schedule.windowOf(s), s.loadGroupId(), s.id());
        }
        UUID previous = s.vehicleId();
        boolean inRoute = s.status() == Shipment.Status.IN_TRANSIT;
        s.changeVehicle(v.id(), v.plate(), why);
        shipments.save(s);
        if (inRoute) fleet.swapVehicleInRoute(previous, v.id());
        audit.record(tenant.profileId(), tenant.orgId(), "SHIPMENT_VEHICLE_CHANGED", "shipment", s.id(),
                previous + " -> " + v.id() + (why == null ? "" : " motivo=" + why));
        ShipmentSummary result = queries.detail(s.id());
        reassigned.fire(new ShipmentReassigned(s.id(), s.requesterOrgId(), s.carrierOrgId(), "VEHICLE", v.plate(),
                s.driverName(), why, inRoute, result.routeLabel()));
        return result;
    }

    private ShipmentSummary doCancel(Shipment s, String reason, boolean operator, String auditAction) {
        boolean wasInTransit = s.status() == Shipment.Status.IN_TRANSIT;
        s.cancel(tenant.orgId(), reason, operator, OffsetDateTime.now());
        shipments.save(s);
        if (wasInTransit) fleet.finishTrip(s.vehicleId(), s.driverId());
        audit.record(tenant.profileId(), tenant.orgId(), auditAction, "shipment", s.id(), s.cancellation().reason());
        ShipmentSummary result = queries.detail(s.id());
        statusChanged.fire(new ShipmentStatusChanged(s.id(), s.requesterOrgId(), s.carrierOrgId(), s.status(),
                result.routeLabel(), tenant.orgId(), s.cancellation().reason(), s.requestId()));
        return result;
    }
}
