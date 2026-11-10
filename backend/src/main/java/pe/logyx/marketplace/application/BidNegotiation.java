package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.BidEligibilityPolicy;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Operaciones sobre una oferta que necesita negotiation (contra-ofertas). El monto negociado respeta el
 * piso RN03 igual que una oferta nueva.
 */
@ApplicationScoped
public class BidNegotiation {

    @Inject BidRepository bids;
    @Inject ShipmentRequestRepository requests;
    @Inject MarketplaceQueries queries;

    /** La oferta admite negociar {@code amount}: pendiente, solicitud publicada y monto ≥ piso. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Bid ensureNegotiable(UUID bidId, BigDecimal amount) {
        Bid b = queries.bidForParty(bidId);
        ShipmentRequest r = requests.findById(b.requestId()).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        if (!b.isPending() || !r.isPublished()) {
            throw BusinessException.conflict("LOGYX-NEG-005", "La oferta ya no admite contra-ofertas");
        }
        BidEligibilityPolicy.ensureAboveFloor(amount, r.pricing().priceFloor());                             // RN03
        return b;
    }

    /** RF28: el transportista aceptó la contra-oferta; el monto de su oferta pasa a {@code amount}. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Bid reviseAmount(UUID bidId, BigDecimal amount) {
        Bid b = ensureNegotiable(bidId, amount);
        b.reviseAmount(amount);
        bids.save(b);
        return b;
    }
}
