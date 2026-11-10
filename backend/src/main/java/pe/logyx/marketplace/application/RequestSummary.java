package pe.logyx.marketplace.application;

import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;

/**
 * Solicitud tal como se muestra: subasta, conteo agregado de ofertas, nombre
 * de la PYME y (solo para transportistas) su propia oferta.
 */
public record RequestSummary(ShipmentRequest request, Auction auction, long bidCount, String requesterName,
                             BidSummary myBid, boolean requesterRucVerified, BigDecimal requesterTrustScore) {}
