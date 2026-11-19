package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.application.OrganizationGuard;
import pe.logyx.marketplace.application.event.BackupRequested;
import pe.logyx.marketplace.application.event.BackupResponded;
import pe.logyx.marketplace.application.port.out.AwardedShipments;
import pe.logyx.marketplace.application.port.out.BidBackupRepository;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.BackupAwardPolicy;
import pe.logyx.marketplace.domain.model.BackupState;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.BidBackup;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Segunda oferta (roadmap 2.9). Al adjudicar, {@link AcceptBidUseCase} guarda las otras ofertas como respaldo
 * ({@code bid_backups}, por posición). Si el envío adjudicado se cancela en las 24 h siguientes ({@link BackupAwardPolicy}):
 * la PYME le pide al siguiente transportista que confirme, él responde y, si confirmó, la PYME le adjudica la solicitud
 * (nuevo envío, misma comisión vigente). Cada paso corre bajo la RLS de quien lo hace: el transportista solo ve y
 * responde su propia fila y solo cuando se le pidió; nunca ve su posición ni las demás ofertas (RN04).
 */
@ApplicationScoped
public class BackupBidService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Una oferta de respaldo con su oferta y el perfil del transportista (para la PYME). */
    public record Item(BidSummary bid, BidBackup backup) {}

    /** Lo que la PYME ve de una solicitud: si se puede adjudicar a otra oferta, hasta cuándo y los candidatos. */
    public record Options(UUID requestId, boolean eligible, String blockerCode, String blockerMessage,
                          OffsetDateTime deadline, List<Item> items) {}

    /** Lo que el transportista ve de un pedido de confirmación: su propia oferta y la carga. */
    public record CarrierRequest(BidBackup backup, Bid bid, ShipmentRequest request, String requesterName) {}

    @Inject BidBackupRepository backups;
    @Inject BidRepository bids;
    @Inject ShipmentRequestRepository requests;
    @Inject AwardedShipments shipments;
    @Inject AcceptBidUseCase acceptBid;
    @Inject RequestSummaries summaries;
    @Inject CarrierPreferenceService preferences;
    @Inject OrganizationDirectory organizations;
    @Inject OrganizationGuard guard;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<BackupRequested> requested;
    @Inject Event<BackupResponded> responded;

    // ----- Al adjudicar (RN05): llamado por AcceptBidUseCase dentro de su transacción

    /** Guarda como respaldo las ofertas que la adjudicación rechaza, de la mejor a la peor. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void keep(List<Bid> rejected) {
        List<Bid> sorted = rejected.stream()
                .sorted(java.util.Comparator.comparing(Bid::amount).thenComparing(Bid::createdAt)).toList();
        for (int i = 0; i < sorted.size(); i++) backups.save(BidBackup.keep(sorted.get(i), i + 1));
    }

    // ----- PYME

    @Transactional
    public Options options(UUID requestId) {
        ShipmentRequest r = ownedRequest(requestId);
        BusinessException blocker = blocker(r);
        List<BidBackup> all = backups.listByRequest(r.id());
        Map<UUID, Bid> byId = bids.listByRequest(r.id()).stream().collect(Collectors.toMap(Bid::id, Function.identity()));
        List<Bid> ofBackups = all.stream().map(b -> byId.get(b.bidId())).filter(java.util.Objects::nonNull).toList();
        Map<UUID, BidSummary> summary = summaries.bids(ofBackups).stream()
                .collect(Collectors.toMap(s -> s.bid().id(), Function.identity()));
        List<Item> items = all.stream().filter(b -> summary.containsKey(b.bidId()))
                .map(b -> new Item(summary.get(b.bidId()), b)).toList();
        OffsetDateTime deadline = BackupAwardPolicy.deadline(shipments.latest(r.id()).orElse(null));
        return new Options(r.id(), blocker == null, blocker == null ? null : blocker.code(),
                blocker == null ? null : blocker.getMessage(), deadline, items);
    }

    /** La PYME le pide al transportista de esta oferta que confirme que puede llevar la carga. */
    @Transactional
    public Item requestConfirmation(UUID bidId) {
        guard.ensureCanTransact(tenant.orgId());
        BidBackup backup = ownedBackup(bidId);
        ShipmentRequest r = ownedRequest(backup.requestId());
        ensureAwardable(r);
        if (preferences.isBlocked(backup.carrierOrgId())) {
            throw BusinessException.conflict("LOGYX-BID-006", "Bloqueaste a este transportista: quita el bloqueo para continuar");
        }
        backup.request();
        backups.save(backup);
        Bid bid = bids.findForRequester(bidId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Oferta"));
        String requester = organizations.get(tenant.orgId()).legalName();
        audit.record(tenant.profileId(), tenant.orgId(), "BACKUP_BID_REQUESTED", "bid", bidId, "solicitud=" + r.id());
        requested.fire(new BackupRequested(r.id(), bidId, tenant.orgId(), requester, backup.carrierOrgId(), r.routeLabel()));
        return new Item(summaries.bids(List.of(bid)).getFirst(), backup);
    }

    /** La PYME adjudica la solicitud a la oferta de respaldo que el transportista ya confirmó. */
    @Transactional
    public AcceptOutcome award(UUID bidId) {
        guard.ensureCanTransact(tenant.orgId());
        BidBackup backup = ownedBackup(bidId);
        ShipmentRequest r = ownedRequest(backup.requestId());
        ensureAwardable(r);
        backup.use();                                                           // BCK-004 si el transportista no confirmó
        Bid bid = bids.findForRequester(bidId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Oferta"));
        if (preferences.isBlocked(bid.carrierOrgId())) {
            throw BusinessException.conflict("LOGYX-BID-006", "Bloqueaste a este transportista: quita el bloqueo para continuar");
        }
        backups.save(backup);
        return acceptBid.award(r, bid, "BID_BACKUP_AWARDED");
    }

    // ----- Transportista

    /** Pedidos de confirmación (y su resultado) del transportista actual. */
    @Transactional
    public List<CarrierRequest> mine() {
        List<BidBackup> mine = backups.listRequestedForCarrier(tenant.orgId());
        if (mine.isEmpty()) return List.of();
        Map<UUID, Bid> bidsById = bids.listByCarrier(tenant.orgId()).stream()
                .collect(Collectors.toMap(Bid::id, Function.identity()));
        Map<UUID, ShipmentRequest> reqs = requests.listByIds(mine.stream().map(BidBackup::requestId).toList()).stream()
                .collect(Collectors.toMap(ShipmentRequest::id, Function.identity()));
        Map<UUID, String> names = organizations.byIds(mine.stream().map(BidBackup::requesterOrgId)
                .collect(Collectors.toSet())).values().stream()
                .collect(Collectors.toMap(o -> o.id(), o -> o.legalName()));
        return mine.stream().filter(b -> bidsById.containsKey(b.bidId()) && reqs.containsKey(b.requestId()))
                .map(b -> new CarrierRequest(b, bidsById.get(b.bidId()), reqs.get(b.requestId()),
                        names.get(b.requesterOrgId()))).toList();
    }

    /** El transportista confirma (o declina) llevar la carga si la PYME se la adjudica. */
    @Transactional
    public CarrierRequest respond(UUID bidId, boolean accept) {
        BidBackup backup = backups.findByBid(bidId).filter(b -> b.carrierOrgId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Pedido de confirmación"));
        if (accept) guard.ensureCanTransact(tenant.orgId());
        backup.respond(accept);
        backups.save(backup);
        Bid bid = bids.findById(bidId).orElseThrow(() -> BusinessException.notFound("Oferta"));
        ShipmentRequest r = requests.findById(backup.requestId()).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        String carrier = organizations.get(tenant.orgId()).legalName();
        audit.record(tenant.profileId(), tenant.orgId(), accept ? "BACKUP_BID_CONFIRMED" : "BACKUP_BID_DECLINED",
                "bid", bidId, "solicitud=" + r.id());
        responded.fire(new BackupResponded(r.id(), bidId, backup.requesterOrgId(), tenant.orgId(), carrier,
                r.routeLabel(), accept));
        return new CarrierRequest(backup, bid, r, null);
    }

    // ----- helpers

    private ShipmentRequest ownedRequest(UUID requestId) {
        return requests.findOwned(requestId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Solicitud"));
    }

    private BidBackup ownedBackup(UUID bidId) {
        return backups.findByBid(bidId).filter(b -> b.requesterOrgId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Oferta de respaldo"));
    }

    private BusinessException blocker(ShipmentRequest r) {
        try {
            ensureAwardable(r);
            return null;
        } catch (BusinessException e) {
            return e;
        }
    }

    private void ensureAwardable(ShipmentRequest r) {
        BackupAwardPolicy.ensureAwardable(r.status(), shipments.latest(r.id()).orElse(null), r.cargo().pickupDate(),
                LocalDate.now(LIMA));
    }

    /** ¿El estado permite que la PYME pida confirmar? (para la vista: solo AVAILABLE) */
    public static boolean canRequest(BidBackup b) { return b.state() == BackupState.AVAILABLE; }
}
