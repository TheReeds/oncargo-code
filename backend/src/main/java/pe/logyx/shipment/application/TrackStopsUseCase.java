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
import pe.logyx.shipment.application.event.ShipmentStopUpdated;
import pe.logyx.shipment.application.port.out.DeliveryEvidenceProvider;
import pe.logyx.shipment.application.port.out.IncidentHoldProvider;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.DeliveryEvidencePolicy;
import pe.logyx.shipment.domain.StopCompletionPolicy;
import pe.logyx.shipment.domain.StopEventTimePolicy;
import pe.logyx.shipment.domain.StopSequencePolicy;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    @Inject Event<ShipmentStopUpdated> stopUpdated;

    @Transactional
    public ShipmentSummary arrive(UUID shipmentId, UUID stopId) {
        return arrive(shipmentId, stopId, null);
    }

    @Transactional
    public ShipmentSummary complete(UUID shipmentId, UUID stopId) {
        return complete(shipmentId, stopId, (OffsetDateTime) null);
    }

    /**
     * «Llegué». Idempotente (roadmap 1.11): si la llegada a esa parada ya estaba registrada, responde el envío tal cual
     * (sin error, sin eventos), aunque el envío ya se haya cerrado o tenga una incidencia: con mala señal la app
     * reintenta acciones cuya respuesta se perdió. {@code occurredAt}: hora del dispositivo (StopEventTimePolicy).
     */
    @Transactional
    public ShipmentSummary arrive(UUID shipmentId, UUID stopId, OffsetDateTime occurredAt) {
        Shipment s = lockedDriverShipment(shipmentId);
        List<ShipmentStop> all = stops.listByShipment(s.id());
        if (find(all, stopId).map(ShipmentStop::arrivalRecorded).orElse(false)) return queries.detail(s.id());
        ensureCanAdvance(s);
        ShipmentStop stop = StopSequencePolicy.requireNext(all, stopId);
        OffsetDateTime now = OffsetDateTime.now();
        stop.arrive(StopEventTimePolicy.resolve(occurredAt, now, previousStepAt(all, stop)), now);
        stops.save(stop);
        fireStopUpdated(s, stop);
        return queries.detail(s.id());
    }

    /** «Carga recogida» / «Entregado». Idempotente como {@link #arrive}: repetirlo sobre una parada completada da 200. */
    @Transactional
    public ShipmentSummary complete(UUID shipmentId, UUID stopId, OffsetDateTime occurredAt) {
        return complete(shipmentId, stopId, new StopCompletion(occurredAt, null, null, null));
    }

    /**
     * Cierre con los datos de la parada (roadmap 2.5): peso medido en el recojo y quién recibe la entrega
     * ({@link StopCompletionPolicy}). El recojo exige la foto de la carga y el peso si la PYME los pidió.
     */
    @Transactional
    public ShipmentSummary complete(UUID shipmentId, UUID stopId, StopCompletion data) {
        Shipment s = lockedDriverShipment(shipmentId);
        return doComplete(s, stopId, data, null);
    }

    // ----- Registro desde la oficina del transportista (roadmap 2.5): «celular sin batería»

    /**
     * El despachador (ADMIN u OPS del transportista) registra la llegada en nombre del conductor, con motivo
     * obligatorio (LOGYX-SHP-006). Mismas reglas que la app (orden RN08, incidencia RN11, hora acotada) e idempotente;
     * queda marcada en la parada y auditada ({@code STOP_REGISTERED_BY_OFFICE}).
     */
    @Transactional
    public ShipmentSummary arriveByOffice(UUID shipmentId, UUID stopId, String reason, OffsetDateTime occurredAt) {
        String why = requireReason(reason);
        Shipment s = lockedCarrierShipment(shipmentId);
        List<ShipmentStop> all = stops.listByShipment(s.id());
        if (find(all, stopId).map(ShipmentStop::arrivalRecorded).orElse(false)) return queries.detail(s.id());
        ensureCanAdvance(s);
        ShipmentStop stop = StopSequencePolicy.requireNext(all, stopId);
        OffsetDateTime now = OffsetDateTime.now();
        stop.arrive(StopEventTimePolicy.resolve(occurredAt, now, previousStepAt(all, stop)), now);
        stop.markArrivalByOffice(why);
        stops.save(stop);
        audit.record(tenant.profileId(), tenant.orgId(), "STOP_REGISTERED_BY_OFFICE", "shipment", s.id(),
                detail("llegada", stop, why));
        fireStopUpdated(s, stop);
        return queries.detail(s.id());
    }

    /**
     * Cierre de la parada desde la oficina, con motivo. RN06: la oficina <b>no</b> sube la foto de entrega ni la de la
     * carga; si la parada la exige y el conductor no la subió, el cierre se rechaza igual (LOGYX-TRK-006 / TRK-009).
     */
    @Transactional
    public ShipmentSummary completeByOffice(UUID shipmentId, UUID stopId, String reason, StopCompletion data) {
        String why = requireReason(reason);
        Shipment s = lockedCarrierShipment(shipmentId);
        return doComplete(s, stopId, data, why);
    }

    private ShipmentSummary doComplete(Shipment s, UUID stopId, StopCompletion data, String officeReason) {
        List<ShipmentStop> all = stops.listByShipment(s.id());
        if (find(all, stopId).map(ShipmentStop::isCompleted).orElse(false)) return queries.detail(s.id());
        ensureCanAdvance(s);
        ShipmentStop stop = StopSequencePolicy.requireNext(all, stopId);
        stop.ensureArrived();                                                  // F49: TRK-003 antes que TRK-006
        DeliveryEvidencePolicy.ensureEvidence(stop.type(), evidence.hasDeliveryPhoto(s.id(), stop.id()));
        StopCompletion d = data == null ? StopCompletion.NONE : data;
        boolean pickupPhoto = stop.type() == ShipmentStop.Type.PICKUP && s.pickupRequirements().photo()
                && evidence.hasPickupPhoto(s.id(), stop.id());
        StopCompletionPolicy.Data clean = StopCompletionPolicy.validate(stop.type(), s.pickupRequirements(),
                pickupPhoto, d.weightKg(), d.receivedByName(), d.receivedByDoc());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime at = StopEventTimePolicy.resolve(d.occurredAt(), now, stop.arrivedAt());
        stop.complete(at, now);
        stop.recordCompletionData(clean.loadedWeightKg(), clean.receivedByName(), clean.receivedByDoc());
        if (officeReason != null) stop.markCompletionByOffice(officeReason);
        stops.save(stop);
        if (officeReason != null) {
            audit.record(tenant.profileId(), tenant.orgId(), "STOP_REGISTERED_BY_OFFICE", "shipment", s.id(),
                    detail("cierre", stop, officeReason));
        }

        Shipment.Progress progress = s.onStopCompleted(stop.type(), StopSequencePolicy.allCompleted(all), at);
        shipments.save(s);
        if (progress.tripStarted()) fleet.startTrip(s.vehicleId(), s.driverId());
        if (progress.delivered()) {
            fleet.finishTrip(s.vehicleId(), s.driverId());
            audit.record(tenant.profileId(), tenant.orgId(), "SHIPMENT_DELIVERED", "shipment", s.id(), null);
        }
        ShipmentSummary result = queries.detail(s.id());
        fireStopUpdated(s, stop);
        if (progress.tripStarted() || progress.delivered()) {
            statusChanged.fire(new ShipmentStatusChanged(s.id(), s.requesterOrgId(), s.carrierOrgId(), s.status(),
                    result.routeLabel()));
        }
        return result;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.strip().length() < 3) {
            throw BusinessException.badRequest("LOGYX-SHP-006",
                    "Indica el motivo: por qué la oficina registra este paso en lugar del conductor", "reason");
        }
        String r = reason.strip();
        return r.length() > 300 ? r.substring(0, 300) : r;
    }

    private static String detail(String what, ShipmentStop stop, String reason) {
        String d = what + " " + stop.type() + " parada=" + stop.id() + " · motivo: " + reason;
        return d.length() > 500 ? d.substring(0, 499) + "…" : d;
    }

    /** Envío del transportista actual (404 si no es suyo), con el mismo candado que la app del conductor. */
    private Shipment lockedCarrierShipment(UUID id) {
        if (tenant.orgType() != pe.logyx.shared.tenancy.OrgType.CARRIER || tenant.isDriver()) {
            throw BusinessException.notFound("Envío");
        }
        shipments.lockResource("shipment-track:" + id);
        return shipments.findForCarrier(id, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Envío"));
    }

    /**
     * Lo que debe ser anterior a este evento: el cierre de la parada previa (en la primera, nada: solo rige el límite
     * de 48 h; un reloj algo atrasado no debe trabar la cola del conductor).
     */
    private static OffsetDateTime previousStepAt(List<ShipmentStop> all, ShipmentStop stop) {
        return all.stream().filter(x -> x.sequence() < stop.sequence() && x.completedAt() != null)
                .max(Comparator.comparingInt(ShipmentStop::sequence)).map(ShipmentStop::completedAt)
                .orElse(null);
    }

    private static Optional<ShipmentStop> find(List<ShipmentStop> all, UUID stopId) {
        return all.stream().filter(x -> x.id().equals(stopId)).findFirst();
    }

    /** Tiempo real (ADR-0007): la PYME y el transportista ven el avance sin recargar; el conductor, en sus otros dispositivos. */
    private void fireStopUpdated(Shipment s, ShipmentStop stop) {
        stopUpdated.fire(new ShipmentStopUpdated(s.id(), stop.id(), s.requesterOrgId(), s.carrierOrgId(),
                tenant.profileId(), stop.status().name(), s.status().name()));
    }

    /**
     * Envío asignado al conductor actual, con un candado consultivo por envío: dos reintentos simultáneos de la misma
     * acción se serializan y el segundo ve la parada ya actualizada (respuesta idempotente, sin doble efecto).
     */
    private Shipment lockedDriverShipment(UUID id) {
        Driver d = queries.currentDriver();
        // El candado va antes de leer nada del envío: así la lectura ve lo que confirmó el intento anterior.
        shipments.lockResource("shipment-track:" + id);
        return shipments.findForDriver(id, d.id()).orElseThrow(() -> BusinessException.notFound("Envío"));
    }

    private void ensureCanAdvance(Shipment s) {
        s.ensureOpen();
        if (holds.hasActiveIncident(s.id())) {                                                            // RN11
            throw BusinessException.conflict("LOGYX-TRK-005",
                    "El envío tiene una incidencia activa: no se puede avanzar hasta que el operador la resuelva (RN11)");
        }
    }
}
