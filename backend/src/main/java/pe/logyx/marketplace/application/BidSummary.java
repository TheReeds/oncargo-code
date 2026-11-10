package pe.logyx.marketplace.application;

import pe.logyx.marketplace.domain.model.Bid;

import java.math.BigDecimal;

/** Oferta con el perfil público del transportista (incluye su Trust Score, RN09). */
public record BidSummary(Bid bid, String carrierName, boolean carrierRucVerified, BigDecimal carrierTrustScore) {}
