package pe.logyx.pricing;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pe.logyx.catalog.City;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.api.BusinessException;

import java.math.BigDecimal;

@Path("/api/v1/pricing")
@Authenticated
public class PricingResource {

    public record QuoteRequest(@NotBlank String originCity, @NotBlank String destinationCity,
                               @NotNull @DecimalMin(value = "1", message = "El peso debe ser mayor a 0") BigDecimal weightKg,
                               @NotNull CargoType cargoType) {}

    @Inject PricingService pricing;

    @POST
    @Path("/calculate")
    public ApiResponse<PricingService.Quote> calculate(@Valid QuoteRequest req) {
        City o = parseCity(req.originCity());
        City d = parseCity(req.destinationCity());
        if (o == d) throw BusinessException.badRequest("LOGYX-PRC-002", "Origen y destino deben ser distintos");
        return ApiResponse.of(pricing.quote(o, d, req.weightKg(), req.cargoType()));
    }

    public static City parseCity(String code) {
        return City.parse(code).orElseThrow(() ->
                BusinessException.badRequest("LOGYX-PRC-001", "Ciudad fuera del corredor atendido: " + code));
    }
}
