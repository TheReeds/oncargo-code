package pe.logyx.pricing.infrastructure.web;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.pricing.application.CommissionService;
import pe.logyx.pricing.application.PricingService;
import pe.logyx.pricing.domain.CommissionPolicy;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.AuthRateLimits;

import java.math.BigDecimal;

/**
 * Cotizador público (roadmap v2, ítem 1.14): sin JWT, con límite por IP, sin persistir nada. Usa el mismo
 * {@code TariffCalculator} y los km por carretera ({@code routing}) que {@code POST /pricing/calculate}.
 * Rutas bajo {@code /api/v1/public/pricing} (otros recursos públicos usan su propia ruta bajo {@code /api/v1/public}).
 */
@Path("/api/v1/public/pricing")
public class PublicPricingResource {

    public record PublicQuoteRequest(@NotBlank(message = "Elige el origen") String originCity,
                                     @NotBlank(message = "Elige el destino") String destinationCity,
                                     @NotNull(message = "Indica el peso")
                                     @DecimalMin(value = "1", message = "El peso debe ser mayor a 0")
                                     @DecimalMax(value = "60000", message = "El peso máximo para cotizar es 60 000 kg")
                                     BigDecimal weightKg,
                                     @NotNull(message = "Elige el tipo de carga") CargoType cargoType) {}

    /**
     * Cotización de referencia. {@code suggestedPrice} y {@code priceFloor} incluyen IGV; {@code suggestedNetOfVat} y
     * {@code suggestedVat} los desglosan. {@code commissionRate} es la que OnCargo retiene al transportista (la PYME
     * no la paga).
     */
    public record PublicQuote(BigDecimal distanceKm, BigDecimal fuelCost, BigDecimal transportCost,
                              BigDecimal tollCost, BigDecimal handlingFee, BigDecimal suggestedPrice,
                              BigDecimal priceFloor, BigDecimal vatRate, BigDecimal suggestedNetOfVat,
                              BigDecimal suggestedVat, BigDecimal commissionRate) {
        static PublicQuote of(PriceQuote q, AwardedPriceBreakdown b) {
            return new PublicQuote(q.distanceKm(), q.fuelCost(), q.transportCost(), q.tollCost(), q.handlingFee(),
                    q.suggestedPrice(), q.priceFloor(), b.vatRate(), b.netOfVat(), b.vat(), b.commissionRate());
        }
    }

    public record PublicCommission(BigDecimal commissionRate, BigDecimal vatRate, String paidBy) {}

    @Inject PricingService pricing;
    @Inject CommissionService commission;
    @Inject AuthRateLimits limits;
    @Inject HttpServerRequest http;

    @POST
    @Path("/quote")
    @Operation(summary = "Cotización pública de un flete (sin cuenta): precio de referencia, piso y desglose de IGV",
            description = "Sin JWT. Límite por IP (429 LOGYX-AUTH-429 con Retry-After). No persiste nada. Mismo "
                    + "cálculo que POST /pricing/calculate: ciudades del corredor (LOGYX-PRC-001), origen distinto "
                    + "del destino (LOGYX-PRC-002).")
    public ApiResponse<PublicQuote> quote(@Valid PublicQuoteRequest req) {
        limits.beforePublicQuote(clientIp());
        City o = City.require(req.originCity());
        City d = City.require(req.destinationCity());
        PriceQuote q = pricing.quote(o, d, req.weightKg(), req.cargoType());
        return ApiResponse.of(PublicQuote.of(q, commission.breakdown(q.suggestedPrice())));
    }

    @GET
    @Path("/commission")
    @Operation(summary = "Cuánto cobra OnCargo: la comisión vigente (la paga el transportista) y el IGV",
            description = "Sin JWT. Para la página de precios.")
    public ApiResponse<PublicCommission> commission() {
        return ApiResponse.of(new PublicCommission(commission.currentRate(), CommissionPolicy.VAT_RATE, "CARRIER"));
    }

    private String clientIp() {
        var addr = http.remoteAddress();
        return addr == null ? "desconocida" : addr.hostAddress();
    }
}
