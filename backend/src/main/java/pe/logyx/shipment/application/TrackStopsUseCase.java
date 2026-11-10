package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.event.ShipmentStatusChanged;
import pe.logyx.shipment.application.port.out.DeliveryEvidenceProvider;
import pe.logyx.shipment.application.port.out.IncidentHoldProvider;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.DeliveryEvidencePolicy;
import pe.logyx.shipment.domain.StopSequencePolicy;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * App del conductor: llegada y cierre de cada parada, en orden (RN08). Una incidencia activa congela el avance
 * (RN11) y la parada de entrega solo se completa con al menos una foto de evidencia subida antes (RF37).
 */
@ApplicationScoped
public class TrackStopsUseCase {

    @Inject ShipmentRepository shipments;
    @Inject ShipmentStopRepository stops;
    @Inject FleetService fleet;
    @Inject ShipmentQueries queries;
    @Inject IncidentHoldProvider holds;
    @Inject DeliveryEvidenceProvider evidence;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<ShipmentStatusChanged> statusChanged;

    @Transactional
    public ShipmentSummary arrive(UUID shipmentId, UUID stopId) {
        Shipment s = driverShipment(shipmentId);
        ShipmentStop stop = StopSequencePolicy.requireNext(stops.listByShipment(s.id()), stopId);
        stop.arrive(OffsetDateTime.now());
        stops.save(stop);
        return queries.detail(s.id());
    }

    @Transactional
    public ShipmentSummary complete(UUID shipmentId, UUID stopId) {
        Shipment s = driverShipment(shipmentId);
        List<ShipmentStop> all = stops.listByShipment(s.id());
        ShipmentStop stop = StopSequencePolicy.requireNext(all, stopId);
        DeliveryEvidencePolicy.ensureEvidence(stop.type(), evidence.hasDeliveryPhoto(s.id(), stop.id()));
        OffsetDateTime now = OffsetDateTime.now();
        stop.complete(now);
        stops.save(stop);

        Shipment.Progress progress = s.onStopCompleted(stop.type(), StopSequencePolicy.allCompleted(all), now);
        shipments.save(s);
        if (progress.tripStarted()) fleet.startTrip(s.vehicleId(), s.driverId());
        if (progress.delivered()) {
            fleet.finishTrip(s.vehicleId(), s.driverId());
            audit.record(tenant.profileId(), tenant.orgId(), "SHIPMENT_DELIVERED", "shipment", s.id(), null);
        }
        ShipmentSummary result = queries.detail(s.id());
        if (progress.tripStarted() || progress.delivered()) {
            statusChanged.fire(new ShipmentStatusChanged(s.id(), s.requesterOrgId(), s.carrierOrgId(), s.status(),
                    result.routeLabel()));
        }
        return result;
    }

    private Shipment driverShipment(UUID id) {
        Driver d = queries.currentDriver();
        Shipment s = shipments.findForDriver(id, d.id()).orElseThrow(() -> BusinessException.notFound("Envío"));
        s.ensureOpen();
        if (holds.hasActiveIncident(s.id())) {                                                            // RN11
            throw BusinessException.conflict("LOGYX-TRK-005",
                    "El envío tiene una incidencia activa: no se puede avanzar hasta que el operador la resuelva (RN11)");
        }
        return s;
    }
}
