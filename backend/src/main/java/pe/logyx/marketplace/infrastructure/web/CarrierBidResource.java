package pe.logyx.marketplace.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.marketplace.application.ImproveBidCommand;
import pe.logyx.marketplace.application.ImproveBidUseCase;
import pe.logyx.marketplace.application.LaneStatsQueries;
import pe.logyx.marketplace.infrastructure.web.MarketplaceDtos.BidView;
import pe.logyx.marketplace.infrastructure.web.MarketplaceDtos.BidWarningView;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Mejorar la oferta, su historial y el precio de la ruta (roadmap 2.8). Solo transportista. */
@Path("/api/v1")
@RolesAllowed(Roles.CARRIER)
public class CarrierBidResource {

    @Inject ImproveBidUseCase improve;
    @Inject LaneStatsQueries laneStats;
    @Inject pe.logyx.marketplace.application.BatchBidUseCase batch;

    /** Al menos uno de los dos. {@code amount} solo puede ser menor al actual (400 LOGYX-BID-006). */
    public record ImproveBid(@DecimalMin("1") BigDecimal amount, UUID vehicleId) {}

    public record BidRevisionView(OffsetDateTime at, BigDecimal previousAmount, BigDecimal newAmount,
                                  String previousPlate, String newPlate) {}

    /**
     * Mediana adjudicada por ruta y tipo de carga en los últimos 90 días (con IGV), con el rango típico (p25 a p75),
     * redondeada a S/ 5. Solo existe con al menos 5 envíos de 3 transportistas; nunca incluye montos individuales.
     */
    public record LaneStatsView(int samples, int windowDays, BigDecimal median, BigDecimal low, BigDecimal high) {}

    public record BatchBidItem(@jakarta.validation.constraints.NotNull UUID requestId,
                               @jakarta.validation.constraints.NotNull UUID vehicleId,
                               @jakarta.validation.constraints.NotNull @DecimalMin("1") BigDecimal amount,
                               @jakarta.validation.constraints.Size(max = 300) String notes) {}

    public record BatchBids(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 20)
                            List<@Valid BatchBidItem> items) {}

    /** {@code ok=false}: {@code code}/{@code message}/{@code field} dicen por qué; {@code warnings}: avisos de agenda y documentos. */
    public record BatchBidResult(UUID requestId, boolean ok, UUID bidId, List<BidWarningView> warnings, String code,
                                 String message, String field) {}

    @POST
    @Path("/bids/batch")
    @Operation(summary = "2.10.3: ofertar a varias cargas de una vez (hasta 20); no es atómico",
            description = "Cada oferta se evalúa por separado con todas las reglas de ofertar (RN01–RN04, RN02 por fecha, RN07, "
                    + "documentos de flota y bloqueos de la PYME). Responde 200 con el resultado por ítem, en el mismo orden.")
    public ApiResponse<List<BatchBidResult>> batchBids(@Valid BatchBids req) {
        var items = req.items().stream().map(i -> new pe.logyx.marketplace.application.BatchBidUseCase.Item(
                i.requestId(), i.vehicleId(), i.amount(), i.notes())).toList();
        return ApiResponse.of(batch.place(items).stream().map(r -> new BatchBidResult(r.requestId(), r.ok(), r.bidId(),
                r.warnings().stream().map(w -> new BidWarningView(w.code(), w.message())).toList(), r.code(), r.message(),
                r.field())).toList());
    }

    @POST
    @Path("/bids/{id}/improve")
    @Operation(summary = "2.8: mejora la oferta con la subasta abierta: baja el monto y/o cambia el vehículo",
            description = "Solo bajar (400 LOGYX-BID-006 si sube; 400 LOGYX-BID-007 si no cambia nada). El vehículo nuevo debe "
                    + "ser apto (RN02 por fecha, CAP-001..007 y documentos FLT-007/008, como al ofertar). No cuenta como oferta "
                    + "nueva (RN04) y queda el historial. La PYME ve improvedCount/improvedAt en la oferta.")
    public ApiResponse<BidView> improveBid(@PathParam("id") UUID id, @Valid ImproveBid req) {
        if (req.amount() == null && req.vehicleId() == null) {
            throw pe.logyx.shared.domain.BusinessException.badRequest("LOGYX-BID-007",
                    "Baja el monto o cambia el vehículo para mejorar la oferta", "amount");
        }
        var out = improve.improve(id, new ImproveBidCommand(req.amount(), req.vehicleId()));
        return ApiResponse.of(BidView.of(out.summary(),
                out.warnings().stream().map(w -> new BidWarningView(w.code(), w.message())).toList()));
    }

    @GET
    @Path("/bids/{id}/revisions")
    @Operation(summary = "Historial de mejoras de mi oferta, la más antigua primero (solo el transportista dueño)")
    public ApiResponse<List<BidRevisionView>> revisions(@PathParam("id") UUID id) {
        return ApiResponse.of(improve.revisions(id).stream().map(r -> new BidRevisionView(r.at(),
                r.revision().previousAmount(), r.revision().newAmount(), r.revision().previousPlate(),
                r.revision().newPlate())).toList());
    }

    @GET
    @Path("/shipment-requests/{id}/lane-stats")
    @Operation(summary = "2.8: mediana adjudicada de la ruta y el tipo de carga de la solicitud (90 días, k >= 5)",
            description = "data es null cuando no hay al menos 5 envíos de 3 transportistas en la ruta: no se muestra nada. "
                    + "Montos con IGV, redondeados a S/ 5; nunca montos individuales ni nombres.")
    public ApiResponse<LaneStatsView> laneStats(@PathParam("id") UUID id) {
        return ApiResponse.of(laneStats.forRequest(id).map(s -> new LaneStatsView(s.samples(),
                LaneStatsQueries.WINDOW_DAYS, s.median(), s.low(), s.high())).orElse(null));
    }
}
