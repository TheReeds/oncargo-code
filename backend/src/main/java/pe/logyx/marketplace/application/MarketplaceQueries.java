package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Lecturas del marketplace para PYME, transportista y otros módulos. */
@ApplicationScoped
@Transactional
public class MarketplaceQueries {

    /** Tope de filas de una exportación CSV (roadmap 1.5). */
    public static final int EXPORT_MAX_ROWS = 10_000;

    @Inject ShipmentRequestRepository requests;
    @Inject BidRepository bids;
    @Inject RequestSummaries summaries;
    @Inject FleetService fleet;
    @Inject VehicleAgenda agenda;
    @Inject TenantContext tenant;

    // ----- PYME

    public List<RequestSummary> myRequests() {
        return summaries.of(requests.listByRequester(tenant.orgId()), false);
    }

    /** Igual que {@link #myRequests()} pero paginado (los recientes primero). */
    public Paged<RequestSummary> myRequests(PageRequest page) {
        Paged<ShipmentRequest> p = Paged.slice(requests.listByRequester(tenant.orgId()), page);
        return new Paged<>(summaries.of(p.items(), false), page, p.total());
    }

    /** "Mis solicitudes" filtradas, ordenadas y paginadas en el servidor. */
    public Paged<RequestSummary> myRequests(MyRequestFilter filter, PageRequest page) {
        Paged<ShipmentRequest> p = requests.searchMine(tenant.orgId(), filter, page);
        return new Paged<>(summaries.of(p.items(), false), page, p.total());
    }

    /** Sin paginar, para la exportación CSV: hasta {@link #EXPORT_MAX_ROWS} filas o 400 si el filtro trae más. */
    public List<RequestSummary> exportMine(MyRequestFilter filter) {
        Paged<ShipmentRequest> p = requests.searchMine(tenant.orgId(), filter, new PageRequest(0, 1));
        if (p.total() > EXPORT_MAX_ROWS) {
            throw BusinessException.badRequest("LOGYX-EXP-001", "Hay " + p.total() + " solicitudes: el máximo para exportar es "
                    + EXPORT_MAX_ROWS + ". Acota por estado, ciudad o fechas", "status");
        }
        List<ShipmentRequest> all = new java.util.ArrayList<>();
        for (int page = 0; all.size() < p.total(); page++) {
            var chunk = requests.searchMine(tenant.orgId(), filter, new PageRequest(page, PageRequest.MAX_SIZE)).items();
            if (chunk.isEmpty()) break;
            all.addAll(chunk);
        }
        return summaries.of(all, false);
    }

    public RequestCounts myRequestCounts() {
        return requests.countMine(tenant.orgId());
    }

    /** Duplicado reciente: publicadas por la PYME en las últimas 24 h con la misma ruta, tipo, peso y fecha de recojo. */
    public List<ShipmentRequest> similarRecent(String originCity, String destinationCity, CargoType cargoType,
                                               BigDecimal weightKg, java.time.LocalDate pickupDate) {
        return requests.findSimilar(tenant.orgId(), City.require(originCity), City.require(destinationCity), cargoType,
                weightKg, pickupDate, java.time.OffsetDateTime.now().minusHours(24), 3);
    }

    /**
     * Ofertas de una solicitud, de menor a mayor monto: las de la PYME dueña (404 si es ajena) o, para el operador,
     * las de cualquier solicitud (RLS bids_operator). Un transportista no entra aquí: sus ofertas van en {@code myBid}.
     */
    public List<BidSummary> bidsFor(UUID requestId) {
        ShipmentRequest r = (tenant.orgType() == OrgType.OPERATOR
                ? requests.findById(requestId) : requests.findOwned(requestId, tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Solicitud"));
        return summaries.bids(bids.listByRequest(r.id()));
    }

    // ----- Transportista

    /** Marketplace: solicitudes publicadas cuya subasta sigue abierta. */
    public Paged<RequestSummary> openMarketplace(MarketplaceFilter filter, PageRequest page) {
        Paged<ShipmentRequest> p = requests.searchOpenMarketplace(filter, page);
        return new Paged<>(summaries.of(p.items(), true), page, p.total());
    }

    /**
     * Solicitudes publicadas con subasta abierta (hasta {@code max}, recojo más próximo primero), con su resumen.
     * Para cargas de retorno (trip): el transportista actual.
     */
    public List<RequestSummary> openRequests(int max) {
        return summaries.of(openRequestsForPlanning(max), true);
    }

    /**
     * Igual que {@link #openRequests(int)} pero sin resúmenes ni contexto de usuario: lo usa el planificador
     * programado (sin JWT, rol dueño), que no necesita nombres ni conteos.
     */
    public List<ShipmentRequest> openRequestsForPlanning(int max) {
        List<ShipmentRequest> out = new java.util.ArrayList<>();
        for (int page = 0; out.size() < max; page++) {
            Paged<ShipmentRequest> p = requests.searchOpenMarketplace(MarketplaceFilter.none(),
                    new PageRequest(page, PageRequest.MAX_SIZE));
            out.addAll(p.items());
            if (p.items().size() < PageRequest.MAX_SIZE) break;
        }
        return out.size() > max ? out.subList(0, max) : out;
    }

    /**
     * KPIs del dashboard. PYME: solicitudes activas y ofertas por revisar. Transportista: cargas abiertas
     * que caben en su vehículo disponible más grande (incluye las que ya ofertó) y ofertas pendientes.
     */
    public DashboardCounts dashboardCounts() {
        UUID org = tenant.orgId();
        if (tenant.orgType() == OrgType.CARRIER) {
            BigDecimal biggest = fleet.vehicles().stream().filter(v -> !v.isBlockedForBids())
                    .map(Vehicle::maxWeightKg).max(BigDecimal::compareTo).orElse(null);
            long compatible = biggest == null ? 0 : requests.searchOpenMarketplace(
                    new MarketplaceFilter(null, null, null, null, biggest, null, MarketplaceFilter.Sort.PICKUP),
                    new PageRequest(0, 1)).total();
            return new DashboardCounts(0, 0, compatible, bids.countByCarrierAndStatus(org, Bid.Status.PENDING));
        }
        return new DashboardCounts(requests.countPublishedByRequester(org), bids.countPendingToReview(org), 0, 0);
    }

    /** Resultado de {@link #vehicleEligibility}: ventana de la carga, tope de ofertas solapadas y un veredicto por vehículo. */
    public record FleetEligibility(pe.logyx.shipment.domain.ResourceSchedulePolicy.Window window, int maxOverlapping,
                                   List<VehicleAgenda.Assessment> vehicles) {}

    /** RN02 por fecha: elegibilidad de cada vehículo activo del transportista actual para la solicitud (404 si no la ve). */
    public FleetEligibility vehicleEligibility(UUID requestId) {
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        return new FleetEligibility(agenda.windowOf(r), agenda.maxOverlapping(), agenda.assessFleet(r));
    }

    /** Ofertas PENDING del vehículo (de la organización actual, por RLS): bloquean archivarlo (higiene de flota). */
    public int pendingBidsOfVehicle(UUID vehicleId) {
        return bids.listPendingByVehicle(vehicleId).size();
    }

    /** Mis ofertas con su solicitud, como resumen (para la exportación CSV: misma consulta y RLS que /my-bids). */
    public List<RequestSummary> myBidSummaries() {
        return myBidRequests().stream().filter(s -> s.myBid() != null).toList();
    }

    public List<RequestSummary> myBidRequests() {
        List<UUID> ids = bids.listByCarrier(tenant.orgId()).stream().map(Bid::requestId).toList();
        if (ids.isEmpty()) return List.of();
        return summaries.of(requests.listByIds(ids), true);
    }

    // ----- Común

    public RequestSummary detail(UUID requestId) {
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        boolean isOwner = r.isOwnedBy(tenant.orgId());
        if (!isOwner && tenant.orgType() == OrgType.PYME) {
            throw BusinessException.notFound("Solicitud");
        }
        return summaries.of(r, !isOwner);
    }

    /** Solicitud aún publicada, sin filtro de usuario: solo para jobs y observadores de sistema (alertas, 2.3). */
    public java.util.Optional<ShipmentRequest> publishedForSystem(UUID requestId) {
        return requests.findById(requestId).filter(ShipmentRequest::isPublished);
    }

    // ----- Para negotiation

    /**
     * Solicitud visible para el usuario actual (RLS): la PYME dueña, un transportista si está publicada o
     * adjudicada a él, o el operador. Una PYME ajena recibe 404.
     */
    public ShipmentRequest requestForParticipant(UUID requestId) {
        ShipmentRequest r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (tenant.orgType() == OrgType.PYME && !r.isOwnedBy(tenant.orgId())) throw BusinessException.notFound("Solicitud");
        return r;
    }

    /** Oferta de un transportista en una solicitud, si existe (visible para la PYME dueña y ese transportista). */
    public java.util.Optional<Bid> bidOfCarrier(UUID requestId, UUID carrierOrgId) {
        return bids.listByRequestsAndCarrier(List.of(requestId), carrierOrgId).stream().findFirst();
    }

    /** Oferta en la que el usuario actual es parte (PYME de la solicitud o transportista dueño); si no, 404. */
    public Bid bidForParty(UUID bidId) {
        UUID me = tenant.orgId();
        return bids.findById(bidId).filter(b -> me.equals(b.requesterOrgId()) || me.equals(b.carrierOrgId()))
                .orElseThrow(() -> BusinessException.notFound("Oferta"));
    }

    // ----- Operador (RLS *_operator: lectura global)

    /** RN10: publicadas hace más de {@code cutoff} sin ofertas, las más antiguas primero. */
    public List<RequestSummary> withoutBidsSince(java.time.OffsetDateTime cutoff) {
        return summaries.of(requests.listPublishedWithoutBidsCreatedBefore(cutoff), false);
    }

    /** Subastas abiertas que cierran antes de {@code until}. */
    public List<RequestSummary> auctionsClosingBefore(java.time.OffsetDateTime until) {
        return summaries.of(requests.listWithOpenAuctionClosingBefore(until), false);
    }

    /** Listado global paginado de solicitudes (RLS requests_operator), más recientes primero. */
    public Paged<RequestSummary> allRequests(OperatorRequestFilter filter, PageRequest page) {
        Paged<ShipmentRequest> p = requests.searchAll(filter, page);
        return new Paged<>(summaries.of(p.items(), false), page, p.total());
    }

    public long countRequests(ShipmentRequest.Status status) {
        return requests.countByStatus(status);
    }

    /** Para otros módulos (shipment): solicitudes visibles para el usuario actual. */
    public List<ShipmentRequest> requestsByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return requests.listByIds(ids);
    }
}
