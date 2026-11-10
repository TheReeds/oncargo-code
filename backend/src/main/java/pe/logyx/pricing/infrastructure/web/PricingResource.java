package pe.logyx.pricing.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.pricing.application.PricingService;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.api.ApiResponse;

import java.math.BigDecimal;

@Path("/api/v1/pricing")
@Authenticated
public class PricingResource {

    public record QuoteRequest(@NotBlank String originCity, @NotBlank String destinationCity,
                               @NotNull @DecimalMin(value = "1", message = "El peso debe ser mayor a 0") BigDecimal weightKg,
                               @NotNull CargoType cargoType) {}

    public record Quote(BigDecimal distanceKm, BigDecimal fuelCost, BigDecimal transportCost,
                        BigDecimal tollCost, BigDecimal handlingFee,
                        BigDecimal suggestedPrice, BigDecimal priceFloor) {
        static Quote of(PriceQuote q) {
            return new Quote(q.distanceKm(), q.fuelCost(), q.transportCost(), q.tollCost(),
                    q.handlingFee(), q.suggestedPrice(), q.priceFloor());
        }
    }

    @Inject PricingService pricing;

    @POST
    @Path("/calculate")
    public ApiResponse<Quote> calculate(@Valid QuoteRequest req) {
        City o = City.require(req.originCity());
        City d = City.require(req.destinationCity());
        return ApiResponse.of(Quote.of(pricing.quote(o, d, req.weightKg(), req.cargoType())));
    }
}
