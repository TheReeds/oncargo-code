package pe.logyx.negotiation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.event.BidAccepted;
import pe.logyx.marketplace.application.event.BidWithdrawn;
import pe.logyx.negotiation.application.event.CounterOfferUpdated;
import pe.logyx.negotiation.application.port.out.CounterOfferRepository;
import pe.logyx.negotiation.domain.model.CounterOffer;
import pe.logyx.shared.outbox.Outboxed;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * F40: al adjudicarse la solicitud (RN05) o retirarse una oferta (F08), las contra-ofertas pendientes quedan EXPIRED
 * (antes quedaban PENDING para siempre). Llega por el outbox y corre en la transacción de sistema del despacho;
 * idempotente (solo toca las que siguen pendientes).
 */
@ApplicationScoped
public class CounterOfferExpiry {

    @Inject CounterOfferRepository counterOffers;
    @Inject Event<CounterOfferUpdated> updated;

    void onAccepted(@Observes @Outboxed BidAccepted e) {
        expire(counterOffers.listPendingByRequest(e.requestId()));
    }

    void onWithdrawn(@Observes @Outboxed BidWithdrawn e) {
        expire(counterOffers.findPending(e.bidId()).stream().toList());
    }

    private void expire(List<CounterOffer> pending) {
        OffsetDateTime now = OffsetDateTime.now();
        for (CounterOffer co : pending) {
            co.expire(now);
            counterOffers.save(co);
            updated.fire(new CounterOfferUpdated(co.id(), co.bidId(), co.requestId(), co.requesterOrgId(),
                    co.carrierOrgId(), co.status(), co.amount(), co.offeredByOrgId()));
        }
    }
}
