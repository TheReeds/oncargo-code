package pe.logyx.operator.infrastructure.web;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.marketplace.application.AcceptOutcome;
import pe.logyx.marketplace.application.ManualAssignmentCommand;
import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.operator.application.AssignmentCandidate;
import pe.logyx.operator.application.OperatorAlerts;
import pe.logyx.operator.application.OperatorMetrics;
import pe.logyx.shipment.application.ShipmentSummary;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class OperatorDtos {
    private OperatorDtos() {}

    /** RN10. {@code amount} opcional: por omisión el precio sugerido (nunca bajo el piso RN03). */
    public record ManualAssignment(@NotNull UUID carrierOrgId, @NotNull UUID vehicleId,
                                   @DecimalMin("1") BigDecimal amount, @Size(max = 300) String notes) {
        ManualAssignmentCommand toCommand() {
            return new ManualAssignmentCommand(carrierOrgId, vehicleId, amount, notes);
        }
    }

    /** Solicitud en riesgo. {@code hoursWithoutBids}: horas desde la publicación si no tiene ofertas. */
    public record SlaRequestView(UUID id, UUID requesterOrgId, String requesterName, City originCity,
                                 City destinationCity, CargoType cargoType, BigDecimal weightKg, LocalDate pickupDate,
                                 BigDecimal suggestedPrice, BigDecimal priceFloor, OffsetDateTime createdAt,
                                 long bidCount, long hoursWithoutBids, Auction.Status auctionStatus,
                                 OffsetDateTime auctionClosesAt) {
        static SlaRequestView of(RequestSummary s, OffsetDateTime now) {
            ShipmentRequest r = s.request();
            Auction a = s.auction();
            return new SlaRequestView(r.id(), r.requesterOrgId(), s.requesterName(), r.cargo().originCity(),
                    r.cargo().destinationCity(), r.cargo().cargoType(), r.cargo().weightKg(), r.cargo().pickupDate(),
                    r.pricing().suggestedPrice(), r.pricing().priceFloor(), r.createdAt(), s.bidCount(),
                    s.bidCount() == 0 ? Duration.between(r.createdAt(), now).toHours() : 0,
                    a == null ? null : a.status(), a == null ? null : a.closesAt());
        }
    }

    public record IncidentAlertView(UUID incidentId, UUID shipmentId, IncidentCategory category,
                                    IncidentStatus status, String requesterName, String carrierName,
                                    City originCity, City destinationCity, OffsetDateTime reportedAt,
                                    long hoursOpen) {
        static IncidentAlertView of(IncidentSummary s, ShipmentSummary sh, OffsetDateTime now) {
            Incident i = s.incident();
            var info = sh == null ? null : sh.request();
            return new IncidentAlertView(i.id(), i.shipmentId(), i.category(), i.status(), s.name(i.requesterOrgId()),
                    s.name(i.carrierOrgId()), info == null ? null : info.originCity(),
                    info == null ? null : info.destinationCity(), i.createdAt(),
                    Duration.between(i.createdAt(), now).toHours());
        }
    }

    /**
     * {@code requestsWithoutBids}: publicadas hace ≥ slaHours sin ofertas (asignables, RN10).
     * {@code expiringAuctions}: subastas abiertas que cierran en ≤ expiringWindowMinutes.
     * {@code shipmentsWithIncidents}: envíos retenidos por una incidencia activa (RN11).
     */
    public record OperatorAlertsView(long slaHours, long expiringWindowMinutes,
                                     List<SlaRequestView> requestsWithoutBids,
                                     List<SlaRequestView> expiringAuctions,
                                     List<IncidentAlertView> shipmentsWithIncidents) {
        static OperatorAlertsView of(OperatorAlerts a) {
            OffsetDateTime now = OffsetDateTime.now();
            return new OperatorAlertsView(a.sla().toHours(), a.expiringWindow().toMinutes(),
                    a.withoutBids().stream().map(r -> SlaRequestView.of(r, now)).toList(),
                    a.expiringAuctions().stream().map(r -> SlaRequestView.of(r, now)).toList(),
                    a.activeIncidents().stream().map(i -> IncidentAlertView.of(i,
                            a.incidentShipments().get(i.incident().shipmentId()), now)).toList());
        }
    }

    public record OperatorMetricsView(long openRequests, long requestsAtRisk, long matchedRequests,
                                      long cancelledRequests, long shipmentsPending, long shipmentsInTransit,
                                      long shipmentsDelivered, long activeIncidents, long pymes, long carriers) {
        static OperatorMetricsView of(OperatorMetrics m) {
            return new OperatorMetricsView(m.openRequests(), m.requestsAtRisk(), m.matchedRequests(),
                    m.cancelledRequests(), m.shipmentsPending(), m.shipmentsInTransit(), m.shipmentsDelivered(),
                    m.activeIncidents(), m.pymes(), m.carriers());
        }
    }

    public record AssignmentCandidateView(UUID carrierOrgId, String carrierName, boolean carrierRucVerified,
                                          BigDecimal carrierTrustScore, UUID vehicleId, String vehiclePlate,
                                          Vehicle.Type vehicleType, BigDecimal maxWeightKg) {
        static AssignmentCandidateView of(AssignmentCandidate c) {
            return new AssignmentCandidateView(c.carrier().id(), c.carrier().legalName(), c.carrier().rucVerified(),
                    c.carrierTrustScore(), c.vehicle().id(), c.vehicle().plate(), c.vehicle().type(),
                    c.vehicle().maxWeightKg());
        }
    }

    public record ManualAssignmentResult(UUID shipmentId, UUID bidId, UUID requestId, UUID carrierOrgId,
                                         String carrierName, BigDecimal amount, String vehiclePlate,
                                         Bid.Status bidStatus, boolean assignedByOperator, List<UUID> rejectedBidIds) {
        static ManualAssignmentResult of(AcceptOutcome o) {
            Bid b = o.acceptedBid().bid();
            return new ManualAssignmentResult(o.shipmentId(), b.id(), b.requestId(), b.carrierOrgId(),
                    o.acceptedBid().carrierName(), b.amount(), b.vehiclePlate(), b.status(), b.assignedByOperator(),
                    o.rejectedBidIds());
        }
    }
}
