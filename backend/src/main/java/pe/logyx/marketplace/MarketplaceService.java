package pe.logyx.marketplace;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.City;
import pe.logyx.fleet.FleetService;
import pe.logyx.fleet.Vehicle;
import pe.logyx.marketplace.MarketplaceDtos.*;
import pe.logyx.organization.Organization;
import pe.logyx.pricing.PricingResource;
import pe.logyx.pricing.PricingService;
import pe.logyx.shared.api.BusinessException;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.ShipmentService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class MarketplaceService {

    @Inject TenantContext tenant;
    @Inject PricingService pricing;
    @Inject FleetService fleet;
    @Inject ShipmentService shipments;
    @Inject AuditService audit;
    @Inject SystemTx system;

    @ConfigProperty(name = "logyx.auction.duration") Duration auctionDuration;
    @ConfigProperty(name = "logyx.auction.max-bids") int maxBids;

    // ------------------------------------------------------------ PYME

    /** RN01: solo PYMEs con RUC verificado publican. La subasta abre en el acto. */
    public RequestView publish(CreateRequest req) {
        Organization org = Organization.findById(tenant.orgId());
        if (!org.rucVerified()) {
            throw BusinessException.forbidden("Su organización debe tener el RUC verificado para publicar (RN01)");
        }
        City origin = PricingResource.parseCity(req.originCity());
        City destination = PricingResource.parseCity(req.destinationCity());
        if (origin == destination) {
            throw BusinessException.badRequest("LOGYX-PRC-002", "Origen y destino deben ser distintos");
        }
        var quote = pricing.quote(origin, destination, req.weightKg(), req.cargoType());

        ShipmentRequest r = new ShipmentRequest();
        r.requesterOrgId = org.id;
        r.createdBy = tenant.profileId();
        r.originCity = origin;
        r.originAddress = req.originAddress().trim();
        r.destinationCity = destination;
        r.destinationAddress = req.destinationAddress().trim();
        r.cargoType = req.cargoType();
        r.cargoDescription = req.cargoDescription().trim();
        r.weightKg = req.weightKg();
        r.volumeM3 = req.volumeM3();
        r.pickupDate = req.pickupDate();
        r.distanceKm = quote.distanceKm();
        r.suggestedPrice = quote.suggestedPrice();
        r.priceFloor = quote.priceFloor();
        r.persist();

        Auction a = new Auction();
        a.requestId = r.id;
        a.closesAt = OffsetDateTime.now().plus(auctionDuration);
        a.maxBids = maxBids;
        a.persist();

        audit.record(tenant.profileId(), org.id, "REQUEST_PUBLISHED", "shipment_request", r.id, null);
        return view(r, a, 0, org.legalName, null);
    }

    public List<RequestView> myRequests() {
        List<ShipmentRequest> list = ShipmentRequest.list(
                "requesterOrgId = ?1 order by createdAt desc", tenant.orgId());
        return views(list, false);
    }

    public List<BidView> bidsFor(UUID requestId) {
        ShipmentRequest r = ownRequest(requestId);
        List<Bid> bids = Bid.list("requestId = ?1 order by amount asc, createdAt asc", r.id);
        return bidViews(bids);
    }

    /** RN05: aceptar una oferta rechaza todas las demás y genera el envío. */
    public AcceptResult accept(UUID bidId) {
        Bid bid = Bid.<Bid>find("id = ?1 and requesterOrgId = ?2", bidId, tenant.orgId())
                .firstResultOptional().orElseThrow(() -> BusinessException.notFound("Oferta"));
        ShipmentRequest r = ownRequest(bid.requestId);
        if (r.status != ShipmentRequest.Status.PUBLISHED || bid.status != Bid.Status.PENDING) {
            throw BusinessException.conflict("LOGYX-BID-004", "La solicitud ya no admite aceptar ofertas");
        }
        Auction a = Auction.forRequest(r.id).orElseThrow();
        if (a.status == Auction.Status.OPEN) a.close(Auction.CloseReason.ACCEPTED);

        bid.status = Bid.Status.ACCEPTED;
        List<Bid> others = Bid.list("requestId = ?1 and id <> ?2 and status = ?3",
                r.id, bid.id, Bid.Status.PENDING);
        others.forEach(o -> o.status = Bid.Status.REJECTED);

        r.status = ShipmentRequest.Status.MATCHED;
        r.assignedCarrierOrgId = bid.carrierOrgId;

        UUID shipmentId = shipments.createFromAcceptedBid(r, bid);
        audit.record(tenant.profileId(), tenant.orgId(), "BID_ACCEPTED", "bid", bid.id,
                "monto=" + bid.amount + " envio=" + shipmentId);
        return new AcceptResult(shipmentId, bidViews(List.of(bid)).getFirst(),
                others.stream().map(o -> o.id).toList());
    }

    public RequestView cancel(UUID requestId) {
        ShipmentRequest r = ownRequest(requestId);
        if (r.status != ShipmentRequest.Status.PUBLISHED) {
            throw BusinessException.conflict("LOGYX-REQ-002", "Solo se puede cancelar una solicitud publicada");
        }
        r.status = ShipmentRequest.Status.CANCELLED;
        Auction a = Auction.forRequest(r.id).orElseThrow();
        if (a.status == Auction.Status.OPEN) a.close(Auction.CloseReason.CANCELLED);
        Bid.<Bid>list("requestId = ?1 and status = ?2", r.id, Bid.Status.PENDING)
                .forEach(b -> b.status = Bid.Status.REJECTED);
        return views(List.of(r), false).getFirst();
    }

    // ------------------------------------------------------------ Transportista

    /** Marketplace: solicitudes publicadas cuya subasta sigue abierta. */
    public List<RequestView> openMarketplace() {
        List<ShipmentRequest> list = ShipmentRequest.find(
                "status = ?1 and id in (select a.requestId from Auction a where a.status = ?2) "
                        + "order by pickupDate asc, createdAt desc",
                ShipmentRequest.Status.PUBLISHED, Auction.Status.OPEN).list();
        return views(list, true);
    }

    public List<RequestView> myBidRequests() {
        List<UUID> ids = Bid.<Bid>list("carrierOrgId = ?1 order by createdAt desc", tenant.orgId())
                .stream().map(b -> b.requestId).toList();
        if (ids.isEmpty()) return List.of();
        return views(ShipmentRequest.list("id in ?1 order by createdAt desc", ids), true);
    }

    public BidView placeBid(UUID requestId, PlaceBid req) {
        ShipmentRequest r = ShipmentRequest.<ShipmentRequest>findByIdOptional(requestId)
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (r.status != ShipmentRequest.Status.PUBLISHED) {
            throw BusinessException.conflict("LOGYX-AUC-001", "La subasta no está abierta");
        }
        // Bloqueo pesimista: serializa las ofertas concurrentes de la misma subasta
        // para que el conteo de RN04 sea exacto.
        Auction a = Auction.<Auction>find("requestId", r.id)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        OffsetDateTime now = OffsetDateTime.now();
        if (!a.acceptsBids(now)) {
            throw BusinessException.conflict("LOGYX-AUC-002", "La subasta ya cerró");
        }
        if (Bid.count("auctionId = ?1 and carrierOrgId = ?2", a.id, tenant.orgId()) > 0) {
            throw BusinessException.conflict("LOGYX-BID-003", "Ya enviaste una oferta para esta solicitud");
        }
        // RN03: piso de precio
        if (req.amount().compareTo(r.priceFloor) < 0) {
            throw BusinessException.badRequest("LOGYX-BID-001",
                    "La oferta no puede ser menor al precio mínimo de S/ " + r.priceFloor + " (RN03)");
        }
        // RN02: vehículo propio, disponible y con capacidad
        Vehicle v = fleet.ownVehicle(req.vehicleId());
        if (v.status != Vehicle.Status.AVAILABLE) {
            throw BusinessException.conflict("LOGYX-CAP-002", "El vehículo no está disponible");
        }
        if (v.maxWeightKg.compareTo(r.weightKg) < 0) {
            throw BusinessException.badRequest("LOGYX-CAP-001",
                    "El vehículo no tiene capacidad suficiente (RN02)");
        }

        Bid b = new Bid();
        b.auctionId = a.id;
        b.requestId = r.id;
        b.requesterOrgId = r.requesterOrgId;
        b.carrierOrgId = tenant.orgId();
        b.vehicleId = v.id;
        b.amount = req.amount();
        b.notes = req.notes();
        b.persist();

        // RN04: cierre por número máximo de ofertas. Bajo RLS el transportista solo
        // ve su propia oferta, así que el conteo total se hace en contexto de sistema.
        // La tabla auctions no tiene datos privados: cerrarla bajo RLS está permitido.
        // La transacción de sistema no ve la oferta aún no confirmada: se cuentan
        // las ajenas (ya confirmadas, gracias al lock de la subasta) + la propia.
        UUID me = tenant.orgId();
        long total = system.call(() -> Bid.count("auctionId = ?1 and carrierOrgId <> ?2", a.id, me)) + 1;
        if (total >= a.maxBids) {
            a.close(Auction.CloseReason.MAX_BIDS);
        }
        return bidViews(List.of(b)).getFirst();
    }

    // ------------------------------------------------------------ Común

    public RequestView detail(UUID requestId) {
        ShipmentRequest r = ShipmentRequest.<ShipmentRequest>findByIdOptional(requestId)
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        boolean isOwner = r.requesterOrgId.equals(tenant.orgId());
        if (!isOwner && tenant.orgType() == OrgType.PYME) {
            throw BusinessException.notFound("Solicitud");
        }
        return views(List.of(r), !isOwner).getFirst();
    }

    /** RN04: cierre por tiempo. Lo invoca el scheduler en contexto de sistema. */
    public int closeExpiredAuctions() {
        List<Auction> expired = Auction.list("status = ?1 and closesAt <= ?2",
                Auction.Status.OPEN, OffsetDateTime.now());
        expired.forEach(a -> a.close(Auction.CloseReason.TIMEOUT));
        if (!expired.isEmpty()) Log.infof("Subastas cerradas por tiempo: %d", expired.size());
        return expired.size();
    }

    // ------------------------------------------------------------ helpers

    private ShipmentRequest ownRequest(UUID id) {
        return ShipmentRequest.<ShipmentRequest>find("id = ?1 and requesterOrgId = ?2", id, tenant.orgId())
                .firstResultOptional().orElseThrow(() -> BusinessException.notFound("Solicitud"));
    }

    private List<RequestView> views(List<ShipmentRequest> list, boolean includeMyBid) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(r -> r.id).toList();
        Map<UUID, Auction> auctions = Auction.<Auction>list("requestId in ?1", ids).stream()
                .collect(Collectors.toMap(a -> a.requestId, Function.identity()));
        Map<UUID, Long> counts = bidCounts(auctions.values().stream().map(a -> a.id).toList());
        Map<UUID, String> names = orgNames(list.stream().map(r -> r.requesterOrgId).collect(Collectors.toSet()));
        Map<UUID, BidView> myBids = includeMyBid
                ? bidViews(Bid.list("requestId in ?1 and carrierOrgId = ?2", ids, tenant.orgId())).stream()
                    .collect(Collectors.toMap(BidView::requestId, Function.identity()))
                : Map.of();
        return list.stream().map(r -> {
            Auction a = auctions.get(r.id);
            return view(r, a, a == null ? 0 : counts.getOrDefault(a.id, 0L),
                    names.get(r.requesterOrgId), myBids.get(r.id));
        }).toList();
    }

    private RequestView view(ShipmentRequest r, Auction a, long bidCount, String requesterName, BidView myBid) {
        AuctionView av = a == null ? null
                : new AuctionView(a.id, a.status, a.closesAt, a.maxBids, bidCount, a.closeReason);
        return new RequestView(r.id, r.requesterOrgId, requesterName,
                r.originCity, r.originAddress, r.destinationCity, r.destinationAddress,
                r.cargoType, r.cargoDescription, r.weightKg, r.volumeM3, r.pickupDate,
                r.distanceKm, r.suggestedPrice, r.priceFloor, r.status, r.createdAt, av, myBid);
    }

    /**
     * Número de ofertas por subasta. Un transportista no puede leer las ofertas
     * ajenas (política RLS bids_carrier) pero sí debe saber cuántas hay: el
     * conteo agregado se calcula en contexto de sistema y no expone montos ni
     * competidores.
     */
    private Map<UUID, Long> bidCounts(List<UUID> auctionIds) {
        if (auctionIds.isEmpty()) return Map.of();
        java.util.function.Supplier<Map<UUID, Long>> q = () -> Bid.getEntityManager()
                .createQuery("select b.auctionId, count(b) from Bid b where b.auctionId in :ids group by b.auctionId",
                        Object[].class)
                .setParameter("ids", auctionIds)
                .getResultList().stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        return tenant.orgType() == OrgType.CARRIER ? system.call(q) : q.get();
    }

    private Map<UUID, String> orgNames(Set<UUID> ids) {
        return Organization.<Organization>list("id in ?1", ids).stream()
                .collect(Collectors.toMap(o -> o.id, o -> o.legalName));
    }

    private List<BidView> bidViews(List<Bid> bids) {
        if (bids.isEmpty()) return List.of();
        Set<UUID> orgIds = bids.stream().map(b -> b.carrierOrgId).collect(Collectors.toSet());
        Map<UUID, Organization> orgs = Organization.<Organization>list("id in ?1", orgIds).stream()
                .collect(Collectors.toMap(o -> o.id, Function.identity()));
        return bids.stream().map(b -> {
            Organization c = orgs.get(b.carrierOrgId);
            return new BidView(b.id, b.requestId, b.carrierOrgId, c.legalName, c.rucVerified(),
                    b.vehicleId, b.amount, b.notes, b.status, b.createdAt);
        }).toList();
    }
}
