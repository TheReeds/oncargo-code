package pe.logyx.marketplace.application;

import java.util.List;
import java.util.UUID;

public record AcceptOutcome(UUID shipmentId, BidSummary acceptedBid, List<UUID> rejectedBidIds) {}
