package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Lecturas de envíos para PYME, transportista y conductor. */
@ApplicationScoped
@Transactional
public class ShipmentQueries {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject ShipmentRepository shipments;
    @Inject ShipmentStopRepository stops;
    @Inject RequestInfoProvider requests;
    @Inject OrganizationDirectory organizations;
    @Inject FleetService fleet;
    @Inject TenantContext tenant;

    public List<ShipmentSummary> mine() {
        UUID org = tenant.orgId();
        return summaries(tenant.orgType() == OrgType.CARRIER
                ? shipments.listByCarrier(org)
                : shipments.listByRequester(org));
    }

    /** Igual que {@link #mine()} pero paginado (los recientes primero). */
    public Paged<ShipmentSummary> mine(PageRequest page) {
        UUID org = tenant.orgId();
        Paged<Shipment> p = Paged.slice(tenant.orgType() == OrgType.CARRIER
                ? shipments.listByCarrier(org)
                : shipments.listByRequester(org), page);
        return new Paged<>(summaries(p.items()), page, p.total());
    }

    /** KPIs de envíos; "del mes" cuenta desde el día 1 a las 00:00 en hora de Lima. */
    public ShipmentCounts counts() {
        UUID org = tenant.orgId();
        boolean carrier = tenant.orgType() == OrgType.CARRIER;
        OffsetDateTime monthStart = LocalDate.now(LIMA).withDayOfMonth(1).atStartOfDay(LIMA).toOffsetDateTime();
        return new ShipmentCounts(
                shipments.countByStatus(org, carrier, List.of(Shipment.Status.IN_TRANSIT)),
                shipments.countByStatus(org, carrier, List.of(Shipment.Status.PENDING, Shipment.Status.IN_TRANSIT)),
                shipments.countDeliveredSince(org, carrier, monthStart));
    }

    public ShipmentSummary detail(UUID id) {
        return summaries(List.of(visible(id))).getFirst();
    }

    public List<ShipmentSummary> driverShipments() {
        return summaries(shipments.listActiveByDriver(currentDriver().id()));
    }

    /** Envío activo del conductor: el IN_TRANSIT; si no hay, el PENDING más antiguo; si no, 404 LOGYX-SHP-002. */
    public ShipmentSummary activeForDriver() {
        List<Shipment> active = shipments.listActiveByDriver(currentDriver().id());   // más antiguos primero
        Shipment s = active.stream().filter(x -> x.status() == Shipment.Status.IN_TRANSIT).findFirst()
                .or(() -> active.stream().findFirst())
                .orElseThrow(() -> new BusinessException(404, "LOGYX-SHP-002", "No tienes un envío activo"));
        return summaries(List.of(s)).getFirst();
    }

    /** Conductor del usuario actual o 403. */
    public Driver currentDriver() {
        return fleet.driverForProfile(tenant.profileId())
                .orElseThrow(() -> BusinessException.forbidden("El usuario no es conductor"));
    }

    private Shipment visible(UUID id) {
        UUID org = tenant.orgId();
        Shipment s = shipments.findById(id).orElseThrow(() -> BusinessException.notFound("Envío"));
        if (!s.isParty(org) && tenant.orgType() != OrgType.OPERATOR) throw BusinessException.notFound("Envío");
        if (tenant.isDriver() && !Objects.equals(s.driverId(), currentDriver().id())) {
            throw BusinessException.notFound("Envío");
        }
        return s;
    }

    private List<ShipmentSummary> summaries(List<Shipment> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<ShipmentStop>> byShipment = stops.listByShipments(list.stream().map(Shipment::id).toList())
                .stream().collect(Collectors.groupingBy(ShipmentStop::shipmentId, LinkedHashMap::new,
                        Collectors.toList()));
        var infos = requests.byIds(list.stream().map(Shipment::requestId).toList());
        Set<UUID> orgIds = new HashSet<>();
        list.forEach(s -> { orgIds.add(s.requesterOrgId()); orgIds.add(s.carrierOrgId()); });
        Map<UUID, Organization> orgs = organizations.byIds(orgIds);
        return list.stream().map(s -> new ShipmentSummary(s, byShipment.getOrDefault(s.id(), List.of()),
                name(orgs.get(s.requesterOrgId())), name(orgs.get(s.carrierOrgId())), infos.get(s.requestId())))
                .toList();
    }

    private static String name(Organization o) {
        return o == null ? null : o.legalName();
    }
}
