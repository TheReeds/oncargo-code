package pe.logyx.marketplace.application;

import pe.logyx.marketplace.domain.model.Bid;

/** Oferta con el perfil público del transportista. */
public record BidSummary(Bid bid, String carrierName, boolean carrierRucVerified) {}
