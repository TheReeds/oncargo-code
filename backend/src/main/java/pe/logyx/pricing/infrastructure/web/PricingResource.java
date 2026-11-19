package pe.logyx.pricing.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.pricing.application.CommissionService;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.shared.domain.BusinessException;
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

    /** {@code baseFee} y {@code tariffVersion} (V24, 2.14): tarifa base y versión del tarifario usada. */
    public record Quote(BigDecimal distanceKm, BigDecimal fuelCost, BigDecimal transportCost,
                        BigDecimal tollCost, BigDecimal handlingFee,
                        BigDecimal suggestedPrice, BigDecimal priceFloor, BigDecimal baseFee, int tariffVersion) {
        static Quote of(PriceQuote q) {
            return new Quote(q.distanceKm(), q.fuelCost(), q.transportCost(), q.tollCost(),
                    q.handlingFee(), q.suggestedPrice(), q.priceFloor(), q.baseFee(), q.tariffVersion());
        }
    }

    /**
     * Comisión vigente y, si se pasa {@code amount}, el desglose de ese precio: IGV, comisión y neto del transportista.
     * {@code breakdown} es nulo sin {@code amount}.
     */
    public record CommissionInfo(java.math.BigDecimal commissionRate, java.math.BigDecimal vatRate, Breakdown breakdown) {}

    /** Desglose de un precio adjudicado (IGV incluido en {@code amount}); ver {@code CommissionPolicy}. */
    public record Breakdown(BigDecimal amount, BigDecimal netOfVat, BigDecimal vat, BigDecimal commission,
                            BigDecimal commissionVat, BigDecimal carrierNet) {
        static Breakdown of(AwardedPriceBreakdown b) {
            return new Breakdown(b.amount(), b.netOfVat(), b.vat(), b.commission(), b.commissionVat(), b.carrierNet());
        }
    }

    @Inject PricingService pricing;
    @Inject CommissionService commission;

    @POST
    @Path("/calculate")
    public ApiResponse<Quote> calculate(@Valid QuoteRequest req) {
        City o = City.require(req.originCity());
        City d = City.require(req.destinationCity());
        return ApiResponse.of(Quote.of(pricing.quote(o, d, req.weightKg(), req.cargoType())));
    }

    @GET
    @Path("/commission")
    @Operation(summary = "Comisión vigente de OnCargo (porcentaje que retiene al transportista) y desglose de un monto",
            description = "El precio adjudicado incluye IGV (18 %); la comisión se calcula sobre el valor de venta "
                    + "sin IGV, lleva su propio IGV (commissionVat) y el transportista cobra el neto. Con amount devuelve el desglose; sin él, solo las tasas. "
                    + "Cualquier rol autenticado (también VIEWER: es lectura).")
    public ApiResponse<CommissionInfo> commission(
            @Parameter(description = "Monto en soles a desglosar (opcional, > 0).") @QueryParam("amount") BigDecimal amount) {
        if (amount != null && (amount.signum() <= 0 || amount.compareTo(new BigDecimal("100000000")) > 0)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "El monto debe ser mayor a 0", "amount");
        }
        var rate = commission.currentRate();
        return ApiResponse.of(new CommissionInfo(rate, pe.logyx.pricing.domain.CommissionPolicy.VAT_RATE,
                amount == null ? null : Breakdown.of(commission.breakdown(amount))));
    }
}
