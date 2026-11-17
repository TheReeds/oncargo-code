package pe.logyx.negotiation.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.marketplace.application.BidNegotiation;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.negotiation.application.event.CounterOfferUpdated;
import pe.logyx.negotiation.application.event.MessagePosted;
import pe.logyx.negotiation.application.port.out.CounterOfferRepository;
import pe.logyx.negotiation.application.port.out.MessageRepository;
import pe.logyx.negotiation.domain.ThreadAccessPolicy;
import pe.logyx.negotiation.domain.model.CounterOffer;
import pe.logyx.negotiation.domain.model.Message;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.Money;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chat por solicitud (RF25, RN07) y contra-ofertas en ambos sentidos: la PYME (RF27) o el transportista (RF26)
 * proponen un monto (≥ piso RN03) sobre una oferta pendiente de una solicitud publicada; la otra parte acepta (el
 * monto de la oferta pasa a ser el acordado, RF28) o rechaza. Una sola vigente por oferta: quien recibe una
 * contra-oferta puede responder con otra, que reemplaza a la anterior. Aceptar el nuevo monto no adjudica: la PYME
 * sigue eligiendo qué oferta acepta (RN05). Una vez adjudicada o retirada la oferta, las pendientes vencen (F40).
 */
@ApplicationScoped
public class NegotiationService {

    @Inject ChatThreads threads;
    @Inject MessageRepository messages;
    @Inject CounterOfferRepository counterOffers;
    @Inject BidNegotiation bids;
    @Inject OrganizationDirectory organizations;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<MessagePosted> messagePosted;
    @Inject Event<CounterOfferUpdated> counterOfferUpdated;

    @Transactional
    public MessageSummary post(UUID requestId, UUID carrierOrgId, String body) {
        ShipmentRequest r = threads.open(requestId, carrierOrgId);
        ThreadAccessPolicy.ensureCanPost(state(r), carrierOrgId.equals(r.assignedCarrierOrgId()));
        UUID me = tenant.orgId();
        Message m = Message.write(r.id(), r.requesterOrgId(), carrierOrgId, me, tenant.profileId(), body);   // RN07
        messages.insert(m);
        String sender = organizations.get(me).legalName();
        UUID recipient = me.equals(carrierOrgId) ? r.requesterOrgId() : carrierOrgId;
        messagePosted.fire(new MessagePosted(m.id(), r.id(), carrierOrgId, me, recipient, sender, preview(m.body())));
        return new MessageSummary(m, sender, true);
    }

    /** RF27 (PYME) / RF26 (transportista): nuevo monto sobre una oferta pendiente (≥ piso RN03). */
    @Transactional
    public CounterOffer propose(UUID bidId, BigDecimal amount, String message) {
        Bid b = bids.ensureNegotiable(bidId, amount);
        UUID me = tenant.orgId();
        if (!me.equals(b.requesterOrgId()) && !me.equals(b.carrierOrgId())) {
            throw BusinessException.notFound("Oferta");
        }
        var pending = counterOffers.findPending(b.id());
        if (pending.isPresent() && pending.get().offeredByOrgId().equals(me)) {
            throw BusinessException.conflict("LOGYX-NEG-004", "Ya hay una contra-oferta tuya pendiente para esta oferta");
        }
        CounterOffer co = CounterOffer.propose(b.id(), b.requestId(), b.requesterOrgId(), b.carrierOrgId(), me,
                tenant.profileId(), b.amount(), amount, message);                                        // RN07
        // Responder con otra contra-oferta reemplaza la de la otra parte (queda rechazada, sin aviso aparte).
        pending.ifPresent(p -> { p.reject(OffsetDateTime.now()); counterOffers.save(p); counterOffers.flush(); });
        counterOffers.save(co);
        String who = co.byCarrier() ? "del transportista" : "de la PYME";
        String text = "Contra-oferta " + who + ": " + Money.soles(amount) + " (oferta vigente " + Money.soles(b.amount()) + ")"
                + (co.message() == null ? "" : ". " + co.message());
        messages.insert(Message.system(b.requestId(), b.requesterOrgId(), b.carrierOrgId(), me, text, amount));
        audit.record(tenant.profileId(), tenant.orgId(), "COUNTER_OFFER_PROPOSED", "counter_offer", co.id(),
                "bid=" + b.id() + " monto=" + amount);
        fire(co);
        return co;
    }

    /** RF28: la parte que la recibió acepta; el monto de la oferta pasa a ser el de la contra-oferta. */
    @Transactional
    public CounterOffer accept(UUID counterOfferId) {
        CounterOffer co = forRecipient(counterOfferId);
        co.accept(OffsetDateTime.now());
        bids.reviseAmount(co.bidId(), co.amount());
        counterOffers.save(co);
        messages.insert(Message.system(co.requestId(), co.requesterOrgId(), co.carrierOrgId(), tenant.orgId(),
                "Contra-oferta aceptada: la oferta ahora es de " + Money.soles(co.amount()), co.amount()));
        audit.record(tenant.profileId(), tenant.orgId(), "COUNTER_OFFER_ACCEPTED", "counter_offer", co.id(),
                "bid=" + co.bidId() + " monto=" + co.amount());
        fire(co);
        return co;
    }

    @Transactional
    public CounterOffer reject(UUID counterOfferId) {
        CounterOffer co = forRecipient(counterOfferId);
        co.reject(OffsetDateTime.now());
        counterOffers.save(co);
        messages.insert(Message.system(co.requestId(), co.requesterOrgId(), co.carrierOrgId(), tenant.orgId(),
                "Contra-oferta de " + Money.soles(co.amount()) + " rechazada", co.amount()));
        fire(co);
        return co;
    }

    private CounterOffer forRecipient(UUID id) {
        CounterOffer co = counterOffers.findById(id).orElseThrow(() -> BusinessException.notFound("Contra-oferta"));
        if (!tenant.orgId().equals(co.recipientOrgId())) {
            throw BusinessException.forbidden(co.byCarrier()
                    ? "Solo la PYME de la solicitud responde la contra-oferta del transportista"
                    : "Solo el transportista de la oferta responde la contra-oferta de la PYME");
        }
        return co;
    }

    private void fire(CounterOffer co) {
        counterOfferUpdated.fire(new CounterOfferUpdated(co.id(), co.bidId(), co.requestId(), co.requesterOrgId(),
                co.carrierOrgId(), co.status(), co.amount(), co.offeredByOrgId()));
    }

    private static ThreadAccessPolicy.RequestState state(ShipmentRequest r) {
        return ThreadAccessPolicy.RequestState.valueOf(r.status().name());
    }

    private static String preview(String body) {
        return body.length() <= 120 ? body : body.substring(0, 117) + "...";
    }
}
