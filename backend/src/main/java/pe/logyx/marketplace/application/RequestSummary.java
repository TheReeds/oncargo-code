package pe.logyx.marketplace.application;

import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;

/**
 * Solicitud tal como se muestra: subasta, conteo agregado de ofertas, nombre de la PYME y (solo para
 * transportistas) su propia oferta y su posición sin montos ajenos ({@code myStanding}). {@code bestBid} es la oferta
 * pendiente más baja y solo se arma para la PYME dueña (nunca para transportistas).
 */
public record RequestSummary(ShipmentRequest request, Auction auction, long bidCount, String requesterName,
                             BidSummary myBid, boolean requesterRucVerified, BigDecimal requesterTrustScore,
                             BidSummary bestBid, BidStanding myStanding) {}
