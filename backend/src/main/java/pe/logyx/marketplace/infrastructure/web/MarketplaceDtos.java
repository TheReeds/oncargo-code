package pe.logyx.marketplace.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.AcceptOutcome;
import pe.logyx.marketplace.application.BidSummary;
import pe.logyx.marketplace.application.PlaceBidCommand;
import pe.logyx.marketplace.application.PublishRequestCommand;
import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.marketplace.domain.VehicleRequirementPolicy;
import pe.logyx.fleet.domain.VehicleCargoCompatibility;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MarketplaceDtos {
    private MarketplaceDtos() {}

    public record CreateRequest(
            @NotBlank String originCity,
            @NotBlank @Size(max = 200) String originAddress,
            @NotBlank String destinationCity,
            @NotBlank @Size(max = 200) String destinationAddress,
            @NotNull CargoType cargoType,
            @NotBlank @Size(max = 300) String cargoDescription,
            @NotNull @DecimalMin(value = "1", message = "El peso debe ser mayor a 0") @DecimalMax("40000") BigDecimal weightKg,
            @DecimalMin("0.01") BigDecimal volumeM3,
            @NotNull @FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate) {

        PublishRequestCommand toCommand() {
            return new PublishRequestCommand(originCity, originAddress, destinationCity, destinationAddress,
                    cargoType, cargoDescription, weightKg, volumeM3, pickupDate);
        }
    }

    /** Cancelar una solicitud publicada: motivo opcional (lo ven los transportistas que ofertaron; RN07). */
    public record CancelRequestBody(@Size(max = 500) String reason) {}

    /** Reabrir la subasta (F07) o republicar la solicitud cancelada: nueva fecha de recojo opcional. */
    public record RescheduleRequest(@FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate) {}

    public record AuctionView(UUID id, Auction.Status status, OffsetDateTime closesAt, int maxBids,
                              long bidCount, Auction.CloseReason closeReason) {}

    public record RequestView(UUID id, UUID requesterOrgId, String requesterName,
                              City originCity, String originAddress,
                              City destinationCity, String destinationAddress,
                              CargoType cargoType, String cargoDescription,
                              BigDecimal weightKg, BigDecimal volumeM3, LocalDate pickupDate,
                              BigDecimal distanceKm, BigDecimal suggestedPrice, BigDecimal priceFloor,
                              ShipmentRequest.Status status, OffsetDateTime createdAt,
                              AuctionView auction,
                              /** Solo para el transportista: su propia oferta, si existe. */
                              BidView myBid,
                              /** true si la organización solicitante tiene el RUC verificado. */
                              boolean requesterRucVerified,
                              /** Vehículo mínimo sugerido según peso y tipo de carga (VehicleRequirementPolicy). */
                              Vehicle.Type minVehicleType,
                              /** Trust Score 0–100 de la PYME solicitante (RN09; 75 sin calificaciones). */
                              BigDecimal requesterTrustScore,
                              /** RN02: tipos de vehículo que pueden llevar esta carga (matriz VehicleCargoCompatibility). */
                              List<Vehicle.Type> compatibleVehicleTypes,
                              /** Motivo de cancelación (de la PYME o del envío adjudicado que se canceló). */
                              String cancelReason,
                              /** Solicitud cancelada de la que se republicó esta (F07). */
                              UUID republishedFrom) {

        static RequestView of(RequestSummary s) {
            ShipmentRequest r = s.request();
            var c = r.cargo();
            var p = r.pricing();
            Auction a = s.auction();
            AuctionView av = a == null ? null
                    : new AuctionView(a.id(), a.status(), a.closesAt(), a.maxBids(), s.bidCount(), a.closeReason());
            return new RequestView(r.id(), r.requesterOrgId(), s.requesterName(),
                    c.originCity(), c.originAddress(), c.destinationCity(), c.destinationAddress(),
                    c.cargoType(), c.cargoDescription(), c.weightKg(), c.volumeM3(), c.pickupDate(),
                    p.distanceKm(), p.suggestedPrice(), p.priceFloor(), r.status(), r.createdAt(), av,
                    s.myBid() == null ? null : BidView.of(s.myBid()), s.requesterRucVerified(),
                    VehicleRequirementPolicy.minimumFor(c.weightKg(), c.cargoType()), s.requesterTrustScore(),
                    VehicleCargoCompatibility.allowedFor(c.cargoType()), r.cancelReason(), r.republishedFrom());
        }

        static List<RequestView> of(List<RequestSummary> list) {
            return list.stream().map(RequestView::of).toList();
        }
    }

    public record PlaceBid(@NotNull UUID vehicleId,
                           @NotNull @DecimalMin(value = "1") BigDecimal amount,
                           @Size(max = 300) String notes) {

        PlaceBidCommand toCommand() {
            return new PlaceBidCommand(vehicleId, amount, notes);
        }
    }

    public record BidView(UUID id, UUID requestId, UUID carrierOrgId, String carrierName,
                          boolean carrierRucVerified, UUID vehicleId, BigDecimal amount, String notes,
                          Bid.Status status, OffsetDateTime createdAt,
                          /** Tipo del vehículo ofertado (copiado al ofertar). */
                          Vehicle.Type vehicleType,
                          /** Placa del vehículo ofertado (copiada al ofertar). */
                          String vehiclePlate,
                          /** Trust Score 0–100 del transportista (RN09; 75 sin calificaciones). */
                          BigDecimal carrierTrustScore,
                          /** true si la registró el operador al asignar manualmente (RN10). */
                          boolean assignedByOperator) {

        static BidView of(BidSummary s) {
            Bid b = s.bid();
            return new BidView(b.id(), b.requestId(), b.carrierOrgId(), s.carrierName(), s.carrierRucVerified(),
                    b.vehicleId(), b.amount(), b.notes(), b.status(), b.createdAt(),
                    b.vehicleType(), b.vehiclePlate(), s.carrierTrustScore(), b.assignedByOperator());
        }
    }

    public record AcceptResult(UUID shipmentId, BidView acceptedBid, List<UUID> rejectedBidIds) {
        static AcceptResult of(AcceptOutcome o) {
            return new AcceptResult(o.shipmentId(), BidView.of(o.acceptedBid()), o.rejectedBidIds());
        }
    }
}
