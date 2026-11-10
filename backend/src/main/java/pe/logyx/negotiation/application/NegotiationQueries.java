package pe.logyx.negotiation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.negotiation.application.port.out.CounterOfferRepository;
import pe.logyx.negotiation.application.port.out.MessageRepository;
import pe.logyx.negotiation.domain.model.CounterOffer;
import pe.logyx.negotiation.domain.model.Message;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Slice;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lecturas del chat y contra-ofertas. */
@ApplicationScoped
@Transactional
public class NegotiationQueries {

    public static final int DEFAULT_PAGE = 30;

    @Inject ChatThreads threads;
    @Inject MessageRepository messages;
    @Inject CounterOfferRepository counterOffers;
    @Inject MarketplaceQueries marketplace;
    @Inject OrganizationDirectory organizations;
    @Inject TenantContext tenant;

    /**
     * Hilos de la solicitud. PYME: uno por transportista que ofertó o escribió (mejor oferta primero, luego los
     * que solo preguntaron). Transportista: solo el suyo (aunque aún no tenga mensajes).
     */
    public List<ThreadSummary> threads(UUID requestId) {
        ShipmentRequest r = marketplace.requestForParticipant(requestId);
        Map<UUID, MessageRepository.ThreadStats> stats = messages.threads(r.id()).stream()
                .collect(Collectors.toMap(MessageRepository.ThreadStats::carrierOrgId, Function.identity()));
        LinkedHashMap<UUID, Bid> carriers = new LinkedHashMap<>();
        if (tenant.orgType() == OrgType.CARRIER) {
            carriers.put(tenant.orgId(), marketplace.bidOfCarrier(r.id(), tenant.orgId()).orElse(null));
        } else {
            marketplace.bidsFor(r.id()).forEach(b -> carriers.put(b.bid().carrierOrgId(), b.bid()));
            stats.keySet().stream().filter(c -> !carriers.containsKey(c)).forEach(c -> carriers.put(c, null));
        }
        Map<UUID, Organization> orgs = organizations.byIds(carriers.keySet());
        return carriers.entrySet().stream().map(e -> {
            var st = stats.get(e.getKey());
            Organization o = orgs.get(e.getKey());
            return new ThreadSummary(r.id(), e.getKey(), o == null ? null : o.legalName(), e.getValue(),
                    st == null ? 0 : st.count(), st == null ? null : st.last());
        }).toList();
    }

    /**
     * Historial del hilo por cursor: hasta {@code size} mensajes anteriores a {@code before} (id de mensaje; null
     * = los más recientes), devueltos en orden cronológico. {@code nextCursor} = id del más antiguo devuelto, si hay más.
     */
    public Slice<MessageSummary> history(UUID requestId, UUID carrierOrgId, UUID before, Integer size) {
        int limit = size == null ? DEFAULT_PAGE : size;
        new PageRequest(0, limit);   // valida 1..100 (LOGYX-VAL-001, field size)
        ShipmentRequest r = threads.open(requestId, carrierOrgId);
        Message cursor = before == null ? null : messages.findInThread(before, r.id(), carrierOrgId)
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Cursor inválido", "before"));
        List<Message> page = new ArrayList<>(messages.listThread(r.id(), carrierOrgId,
                cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.id(), limit + 1));
        boolean more = page.size() > limit;
        if (more) page = new ArrayList<>(page.subList(0, limit));
        Collections.reverse(page);
        String next = more ? page.getFirst().id().toString() : null;
        Map<UUID, Organization> orgs = organizations.byIds(Set.of(r.requesterOrgId(), carrierOrgId));
        UUID me = tenant.orgId();
        List<MessageSummary> items = page.stream().map(m -> new MessageSummary(m,
                Optional.ofNullable(orgs.get(m.senderOrgId())).map(Organization::legalName).orElse(null),
                m.senderOrgId().equals(me))).toList();
        return new Slice<>(items, limit, next);
    }

    public List<CounterOffer> counterOffers(UUID bidId) {
        Bid b = marketplace.bidForParty(bidId);
        return counterOffers.listByBid(b.id());
    }
}
