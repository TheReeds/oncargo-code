package pe.logyx.marketplace;

import jakarta.validation.constraints.*;
import pe.logyx.catalog.City;
import pe.logyx.pricing.CargoType;

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
            @NotNull @FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate) {}

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
                              BidView myBid) {}

    public record PlaceBid(@NotNull UUID vehicleId,
                           @NotNull @DecimalMin(value = "1") BigDecimal amount,
                           @Size(max = 300) String notes) {}

    public record BidView(UUID id, UUID requestId, UUID carrierOrgId, String carrierName,
                          boolean carrierRucVerified, UUID vehicleId, BigDecimal amount, String notes,
                          Bid.Status status, OffsetDateTime createdAt) {}

    public record AcceptResult(UUID shipmentId, BidView acceptedBid, List<UUID> rejectedBidIds) {}
}
