package pe.logyx.shipment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.catalog.City;
import pe.logyx.fleet.Driver;
import pe.logyx.fleet.FleetService;
import pe.logyx.fleet.Vehicle;
import pe.logyx.marketplace.Bid;
import pe.logyx.marketplace.ShipmentRequest;
import pe.logyx.organization.Organization;
import pe.logyx.shared.api.BusinessException;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class ShipmentService {

    public record StopView(UUID id, int sequence, ShipmentStop.Type type, City city, String address,
                           ShipmentStop.Status status, OffsetDateTime arrivedAt, OffsetDateTime completedAt) {
        static StopView of(ShipmentStop s) {
            return new StopView(s.id, s.sequence, s.type, s.city, s.address, s.status, s.arrivedAt, s.completedAt);
        }
    }

    public record ShipmentView(UUID id, UUID requestId, String requesterName, String carrierName,
                               City originCity, City destinationCity, String cargoDescription,
                               BigDecimal weightKg, BigDecimal agreedPrice, String vehiclePlate,
                               UUID driverId, String driverName, Shipment.Status status,
                               OffsetDateTime createdAt, OffsetDateTime deliveredAt,
                               List<StopView> stops) {}

    @Inject TenantContext tenant;
    @Inject FleetService fleet;
    @Inject AuditService audit;
    @Inject SystemTx system;

    /** Lo invoca MarketplaceService.accept dentro de la transacción de la PYME. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID createFromAcceptedBid(ShipmentRequest r, Bid bid) {
        // La flota es privada del transportista (RLS): la placa se copia al
        // contrato en contexto de sistema, una sola vez.
        String plate = system.call(() -> Vehicle.<Vehicle>findById(bid.vehicleId).plate);

        Shipment s = new Shipment();
        s.requestId = r.id;
        s.bidId = bid.id;
        s.requesterOrgId = r.requesterOrgId;
        s.carrierOrgId = bid.carrierOrgId;
        s.vehicleId = bid.vehicleId;
        s.vehiclePlate = plate;
        s.agreedPrice = bid.amount;
        s.persist();

        stop(s, 1, ShipmentStop.Type.PICKUP, r.originCity, r.originAddress);
        stop(s, 2, ShipmentStop.Type.DELIVERY, r.destinationCity, r.destinationAddress);
        return s.id;
    }

    public List<ShipmentView> mine() {
        UUID org = tenant.orgId();
        String field = tenant.orgType() == OrgType.CARRIER ? "carrierOrgId" : "requesterOrgId";
        return views(Shipment.list(field + " = ?1 order by createdAt desc", org));
    }

    public ShipmentView detail(UUID id) {
        return views(List.of(visible(id))).getFirst();
    }

    /** El transportista asigna uno de sus conductores. */
    public ShipmentView assignDriver(UUID shipmentId, UUID driverId) {
        Shipment s = Shipment.<Shipment>find("id = ?1 and carrierOrgId = ?2", shipmentId, tenant.orgId())
                .firstResultOptional().orElseThrow(() -> BusinessException.notFound("Envío"));
        if (s.status != Shipment.Status.PENDING) {
            throw BusinessException.conflict("LOGYX-SHP-001", "Solo se reasigna conductor antes del recojo");
        }
        Driver d = fleet.ownDriver(driverId);
        s.driverId = d.id;
        s.driverName = d.profile.fullName;
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_ASSIGNED", "shipment", s.id, d.id.toString());
        return detail(s.id);
    }

    // ------------------------------------------------------------ Conductor

    public List<ShipmentView> driverShipments() {
        Driver d = currentDriver();
        return views(Shipment.list("driverId = ?1 and status in ?2 order by createdAt",
                d.id, List.of(Shipment.Status.PENDING, Shipment.Status.IN_TRANSIT)));
    }

    public ShipmentView arrive(UUID shipmentId, UUID stopId) {
        Shipment s = driverShipment(shipmentId);
        ShipmentStop stop = nextStop(s, stopId);
        if (stop.status != ShipmentStop.Status.PENDING) {
            throw BusinessException.conflict("LOGYX-TRK-002", "Ya se registró la llegada a esta parada");
        }
        stop.status = ShipmentStop.Status.ARRIVED;
        stop.arrivedAt = OffsetDateTime.now();
        return detail(s.id);
    }

    public ShipmentView complete(UUID shipmentId, UUID stopId) {
        Shipment s = driverShipment(shipmentId);
        ShipmentStop stop = nextStop(s, stopId);
        if (stop.status != ShipmentStop.Status.ARRIVED) {
            throw BusinessException.conflict("LOGYX-TRK-003", "Primero registre la llegada a la parada");
        }
        stop.status = ShipmentStop.Status.COMPLETED;
        stop.completedAt = OffsetDateTime.now();

        Vehicle v = Vehicle.findById(s.vehicleId);
        Driver d = Driver.findById(s.driverId);
        if (stop.type == ShipmentStop.Type.PICKUP && s.status == Shipment.Status.PENDING) {
            s.status = Shipment.Status.IN_TRANSIT;
            v.status = Vehicle.Status.IN_ROUTE;
            d.status = Driver.Status.ON_DUTY;
        }
        boolean allDone = ShipmentStop.count("shipmentId = ?1 and status <> ?2",
                s.id, ShipmentStop.Status.COMPLETED) == 0;
        if (allDone) {
            s.status = Shipment.Status.DELIVERED;
            s.deliveredAt = OffsetDateTime.now();
            v.status = Vehicle.Status.AVAILABLE;
            d.status = Driver.Status.AVAILABLE;
            audit.record(tenant.profileId(), tenant.orgId(), "SHIPMENT_DELIVERED", "shipment", s.id, null);
        }
        return detail(s.id);
    }

    // ------------------------------------------------------------ helpers

    private void stop(Shipment s, int seq, ShipmentStop.Type type, City city, String address) {
        ShipmentStop st = new ShipmentStop();
        st.shipmentId = s.id;
        st.requesterOrgId = s.requesterOrgId;
        st.carrierOrgId = s.carrierOrgId;
        st.sequence = seq;
        st.type = type;
        st.city = city;
        st.address = address;
        st.persist();
    }

    private Shipment visible(UUID id) {
        UUID org = tenant.orgId();
        Shipment s = Shipment.<Shipment>findByIdOptional(id)
                .orElseThrow(() -> BusinessException.notFound("Envío"));
        boolean party = org.equals(s.requesterOrgId) || org.equals(s.carrierOrgId);
        if (!party && tenant.orgType() != OrgType.OPERATOR) throw BusinessException.notFound("Envío");
        if (tenant.isDriver() && !Objects.equals(s.driverId, currentDriver().id)) {
            throw BusinessException.notFound("Envío");
        }
        return s;
    }

    private Driver currentDriver() {
        return Driver.findByProfile(tenant.profileId())
                .orElseThrow(() -> BusinessException.forbidden("El usuario no es conductor"));
    }

    private Shipment driverShipment(UUID id) {
        Driver d = currentDriver();
        Shipment s = Shipment.<Shipment>find("id = ?1 and driverId = ?2", id, d.id)
                .firstResultOptional().orElseThrow(() -> BusinessException.notFound("Envío"));
        if (s.status == Shipment.Status.DELIVERED || s.status == Shipment.Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-TRK-004", "El envío ya está cerrado");
        }
        return s;
    }

    /** RN08: solo se puede actuar sobre la primera parada no completada. */
    private ShipmentStop nextStop(Shipment s, UUID stopId) {
        ShipmentStop next = ShipmentStop.<ShipmentStop>find(
                        "shipmentId = ?1 and status <> ?2 order by sequence", s.id, ShipmentStop.Status.COMPLETED)
                .firstResult();
        if (next == null || !next.id.equals(stopId)) {
            throw BusinessException.conflict("LOGYX-TRK-001",
                    "Las paradas deben actualizarse en orden (RN08)");
        }
        return next;
    }

    private List<ShipmentView> views(List<Shipment> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(s -> s.id).toList();
        Map<UUID, List<StopView>> stops = ShipmentStop.<ShipmentStop>list(
                        "shipmentId in ?1 order by sequence", ids).stream()
                .collect(Collectors.groupingBy(st -> st.shipmentId, LinkedHashMap::new,
                        Collectors.mapping(StopView::of, Collectors.toList())));
        Map<UUID, ShipmentRequest> requests = ShipmentRequest.<ShipmentRequest>list("id in ?1",
                        list.stream().map(s -> s.requestId).toList()).stream()
                .collect(Collectors.toMap(r -> r.id, Function.identity()));
        Set<UUID> orgIds = new HashSet<>();
        list.forEach(s -> { orgIds.add(s.requesterOrgId); orgIds.add(s.carrierOrgId); });
        Map<UUID, String> names = Organization.<Organization>list("id in ?1", orgIds).stream()
                .collect(Collectors.toMap(o -> o.id, o -> o.legalName));
        return list.stream().map(s -> {
            ShipmentRequest r = requests.get(s.requestId);
            return new ShipmentView(s.id, s.requestId, names.get(s.requesterOrgId), names.get(s.carrierOrgId),
                    r.originCity, r.destinationCity, r.cargoDescription, r.weightKg, s.agreedPrice,
                    s.vehiclePlate, s.driverId, s.driverName, s.status, s.createdAt, s.deliveredAt,
                    stops.getOrDefault(s.id, List.of()));
        }).toList();
    }
}
