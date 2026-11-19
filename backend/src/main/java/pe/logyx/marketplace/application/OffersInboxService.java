package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.PreferenceKind;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bandeja consolidada de ofertas de la PYME (roadmap 2.10): una fila por solicitud publicada con ofertas pendientes,
 * con la mejor por monto, la mejor por Trust Score y el tiempo que queda. Las ofertas de transportistas bloqueados no
 * cuentan (no se pueden aceptar). Solo lee lo de la PYME dueña (RLS); RN04 no aplica a la PYME: ve los montos.
 */
@ApplicationScoped
@Transactional
public class OffersInboxService {

    /** Una fila: la solicitud y sus ofertas pendientes ordenadas de menor a mayor monto. */
    public record Row(RequestSummary request, List<BidSummary> bids, BidSummary bestByAmount, BidSummary bestByTrust,
                      boolean bestIsPreferred) {}

    @Inject MarketplaceQueries queries;
    @Inject BidRepository bids;
    @Inject RequestSummaries summaries;
    @Inject CarrierPreferenceService preferences;
    @Inject TenantContext tenant;

    public List<Row> inbox() {
        List<RequestSummary> open = queries.exportMine(MyRequestFilter.of("PUBLISHED", null, null, null, null, null,
                true, "pickup"));
        if (open.isEmpty()) return List.of();
        Set<UUID> blocked = preferences.ids(PreferenceKind.BLOCKED);
        Set<UUID> preferred = preferences.ids(PreferenceKind.PREFERRED);
        List<Row> rows = new java.util.ArrayList<>();
        for (RequestSummary s : open) {
            ShipmentRequest r = s.request();
            List<Bid> pending = bids.listPendingByRequest(r.id()).stream()
                    .filter(b -> !blocked.contains(b.carrierOrgId()))
                    .sorted(Comparator.comparing(Bid::amount).thenComparing(Bid::createdAt)).toList();
            if (pending.isEmpty()) continue;
            Map<UUID, BidSummary> byBid = summaries.bids(pending).stream()
                    .collect(Collectors.toMap(b -> b.bid().id(), Function.identity()));
            List<BidSummary> ordered = pending.stream().map(b -> byBid.get(b.id())).toList();
            BidSummary byAmount = ordered.getFirst();
            BidSummary byTrust = ordered.stream().max(Comparator.comparing((BidSummary b) -> trust(b))
                    .thenComparing((BidSummary b) -> b.bid().amount(), Comparator.reverseOrder())).orElse(byAmount);
            rows.add(new Row(s, ordered, byAmount, byTrust, preferred.contains(byAmount.bid().carrierOrgId())));
        }
        return rows;
    }

    private static BigDecimal trust(BidSummary b) {
        return b.carrierTrustScore() == null ? BigDecimal.ZERO : b.carrierTrustScore();
    }
}
