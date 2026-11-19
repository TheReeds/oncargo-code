package pe.logyx.marketplace.application;

import java.util.List;

/** La oferta creada y los avisos de agenda que el cliente debe mostrar. */
public record PlacedBid(BidSummary summary, List<BidWarning> warnings) {}
