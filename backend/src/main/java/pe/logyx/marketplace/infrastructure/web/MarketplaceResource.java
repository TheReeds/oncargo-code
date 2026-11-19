package pe.logyx.marketplace.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import pe.logyx.shared.api.CsvWriter;
import pe.logyx.shared.idempotency.IdempotencyKeys;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import pe.logyx.marketplace.application.*;
import pe.logyx.marketplace.infrastructure.web.MarketplaceDtos.*;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class MarketplaceResource {

    @Inject PublishRequestUseCase publish;
    @Inject PlaceBidUseCase placeBid;
    @Inject AcceptBidUseCase acceptBid;
    @Inject CancelRequestUseCase cancelRequest;
    @Inject MarketplaceQueries queries;
    @Inject AuctionRecoveryUseCase recovery;
    @Inject pe.logyx.pricing.application.CommissionService commission;

    // ----- PYME
    @POST
    @Path("/shipment-requests")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Publica una solicitud. Con la cabecera Idempotency-Key (8-80 caracteres) un reintento o doble "
            + "clic devuelve la solicitud ya creada (cabecera Idempotent-Replayed: true) en vez de crear otra")
    @APIResponse(responseCode = "201", description = "Solicitud creada (o la ya creada con esa Idempotency-Key)",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiResponseRequestView")))
    public RestResponse<ApiResponse<RequestView>> publish(
            @Parameter(description = "Clave de idempotencia (UUID recomendado), válida 24 h por organización.")
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid CreateRequest req) {
        var out = publish.publishOnce(req.toCommand(IdempotencyKeys.validate(idempotencyKey)));
        var builder = RestResponse.ResponseBuilder.create(RestResponse.Status.CREATED,
                ApiResponse.of(RequestView.of(out.summary())));
        if (out.replayed()) builder.header("Idempotent-Replayed", "true");
        return builder.build();
    }

    @GET
    @Path("/shipment-requests/mine")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Mis solicitudes con filtros, orden y paginación en el servidor (meta.total). Sin page ni size "
            + "devuelve todas las que cumplen el filtro")
    public ApiResponse<List<RequestView>> mine(
            @Parameter(description = "Página 0-based. Si se omite page y size, se devuelven todas.")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20 cuando se pagina).")
            @QueryParam("size") Integer size,
            @Parameter(description = "PUBLISHED, MATCHED o CANCELLED.") @QueryParam("status") String status,
            @Parameter(description = "Código de ciudad de origen (p. ej. LIMA).") @QueryParam("originCity") String originCity,
            @Parameter(description = "Código de ciudad de destino.") @QueryParam("destinationCity") String destinationCity,
            @Parameter(description = "Fecha de recojo desde, ISO (yyyy-MM-dd), inclusive.")
            @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
            @Parameter(description = "Fecha de recojo hasta, ISO (yyyy-MM-dd), inclusive.")
            @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
            @Parameter(description = "Texto: código (OC-7K2QX, con o sin guion), referencia propia, ciudad o descripción.")
            @QueryParam("q") String q,
            @Parameter(description = "true: solo publicadas con ofertas por revisar.") @QueryParam("withBids") Boolean withBids,
            @Parameter(description = "Orden: recent (por omisión), pickup, bids, price_desc o price_asc.")
            @QueryParam("sort") String sort) {
        var filter = MyRequestFilter.of(status, originCity, destinationCity, fromDate, toDate, q, withBids, sort);
        if (page == null && size == null) return ApiResponse.of(RequestView.of(queries.exportMine(filter)));
        return ApiResponse.of(queries.myRequests(filter, PageRequest.of(page, size)).map(RequestView::of));
    }

    @GET
    @Path("/shipment-requests/mine/export")
    @Produces(CsvWriter.MEDIA_TYPE)
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Exporta a CSV (UTF-8 con BOM, separador ;, fechas de Lima) las solicitudes que cumplen los "
            + "mismos filtros que /mine. Máximo 10 000 filas: si hay más, LOGYX-EXP-001")
    public Response exportMine(@QueryParam("status") String status, @QueryParam("originCity") String originCity,
                               @QueryParam("destinationCity") String destinationCity,
                               @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
                               @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
                               @QueryParam("q") String q, @QueryParam("withBids") Boolean withBids,
                               @QueryParam("sort") String sort) {
        var filter = MyRequestFilter.of(status, originCity, destinationCity, fromDate, toDate, q, withBids, sort);
        byte[] body = MarketplaceCsv.requests(queries.exportMine(filter));
        String name = "solicitudes-" + java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")) + ".csv";
        return Response.ok(body, CsvWriter.MEDIA_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").build();
    }

    @GET
    @Path("/shipment-requests/mine/counts")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Totales de mis solicitudes por estado, sin filtros (pestañas de filtro rápido)")
    public ApiResponse<RequestCountsView> counts() {
        var c = queries.myRequestCounts();
        return ApiResponse.of(new RequestCountsView(c.all(), c.published(), c.matched(), c.cancelled(), c.withBids()));
    }

    @GET
    @Path("/shipment-requests/mine/similar")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Posibles duplicados: solicitudes publicadas en las últimas 24 h con la misma ruta, tipo de carga, "
            + "peso y fecha de recojo (máximo 3)")
    public ApiResponse<List<SimilarRequest>> similar(
            @QueryParam("originCity") String originCity, @QueryParam("destinationCity") String destinationCity,
            @QueryParam("cargoType") pe.logyx.catalog.domain.CargoType cargoType,
            @QueryParam("weightKg") BigDecimal weightKg,
            @QueryParam("pickupDate") @Schema(type = SchemaType.STRING, format = "date") String pickupDate) {
        if (originCity == null || destinationCity == null || cargoType == null || weightKg == null || pickupDate == null) {
            throw pe.logyx.shared.domain.BusinessException.badRequest("LOGYX-VAL-001",
                    "Indica originCity, destinationCity, cargoType, weightKg y pickupDate", "originCity");
        }
        return ApiResponse.of(queries.similarRecent(originCity, destinationCity, cargoType, weightKg,
                        MarketplaceFilter.date(pickupDate, "pickupDate")).stream()
                .map(r -> new SimilarRequest(r.id(), r.code(), r.createdAt())).toList());
    }

    @GET
    @Path("/shipment-requests/{id}/bids")
    @RolesAllowed({Roles.PYME, Roles.OPERATOR})
    @Operation(summary = "Ofertas de la solicitud, de menor a mayor monto: la PYME dueña o el operador (solo lectura)")
    public ApiResponse<List<BidView>> bids(@PathParam("id") UUID id) {
        return ApiResponse.of(queries.bidsFor(id).stream().map(BidView::of).toList());
    }

    @POST
    @Path("/shipment-requests/{id}/cancel")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Cancela una solicitud publicada (motivo opcional); avisa a los transportistas que ofertaron")
    public ApiResponse<RequestView> cancel(@PathParam("id") UUID id, @Valid CancelRequestBody req) {
        return ApiResponse.of(RequestView.of(cancelRequest.cancel(id, req == null ? null : req.reason())));
    }

    @POST
    @Path("/shipment-requests/{id}/reopen")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "F07: reabre la subasta que cerró por tiempo (LOGYX-AUC-003); admite nueva fecha de recojo")
    public ApiResponse<RequestView> reopen(@PathParam("id") UUID id, @Valid RescheduleRequest req) {
        return ApiResponse.of(RequestView.of(recovery.reopen(id, req == null ? null : req.pickupDate())));
    }

    @POST
    @Path("/shipment-requests/{id}/republish")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    @Operation(summary = "F07/F06: publica de nuevo una solicitud cancelada (misma carga, precio y subasta nuevos)")
    public ApiResponse<RequestView> republish(@PathParam("id") UUID id, @Valid RescheduleRequest req) {
        return ApiResponse.of(RequestView.of(recovery.republish(id, req == null ? null : req.pickupDate())));
    }

    @POST
    @Path("/bids/{id}/withdraw")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "F08: el transportista retira su oferta pendiente mientras la subasta está abierta "
            + "(LOGYX-BID-005); no puede volver a ofertar en esa subasta")
    public ApiResponse<BidView> withdraw(@PathParam("id") UUID id) {
        return ApiResponse.of(BidView.of(placeBid.withdraw(id)));
    }

    @POST
    @Path("/bids/{id}/accept")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<AcceptResult> accept(@PathParam("id") UUID id) {
        return ApiResponse.of(AcceptResult.of(acceptBid.accept(id)));
    }

    // ----- Transportista
    @GET
    @Path("/shipment-requests")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<RequestView>> marketplace(
            @Parameter(description = "Código de ciudad de origen (p. ej. LIMA).")
            @QueryParam("originCity") String originCity,
            @Parameter(description = "Código de ciudad de destino (p. ej. JULIACA).")
            @QueryParam("destinationCity") String destinationCity,
            @Parameter(description = "Fecha de recojo mínima, ISO (yyyy-MM-dd), inclusive.")
            @QueryParam("pickupFrom") @Schema(type = SchemaType.STRING, format = "date") String pickupFrom,
            @Parameter(description = "Fecha de recojo máxima, ISO (yyyy-MM-dd), inclusive.")
            @QueryParam("pickupTo") @Schema(type = SchemaType.STRING, format = "date") String pickupTo,
            @Parameter(description = "Peso máximo de la carga en kg (inclusive).")
            @QueryParam("maxWeightKg") BigDecimal maxWeightKg,
            @Parameter(description = "Tipo de carga (GENERAL, FRAGILE, PERISHABLE, HAZARDOUS, BULK, OVERSIZED).")
            @QueryParam("cargoType") String cargoType,
            @Parameter(description = "Orden: pickup (por omisión), price_desc o recent.")
            @QueryParam("sort") String sort,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = MarketplaceFilter.of(originCity, destinationCity,
                MarketplaceFilter.date(pickupFrom, "pickupFrom"), MarketplaceFilter.date(pickupTo, "pickupTo"), maxWeightKg,
                cargoType, sort);
        return ApiResponse.of(queries.openMarketplace(filter, PageRequest.of(page, size)).map(RequestView::of));
    }

    @GET
    @Path("/shipment-requests/my-bids")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<RequestView>> myBids() {
        return ApiResponse.of(RequestView.of(queries.myBidRequests()));
    }

    @GET
    @Path("/shipment-requests/my-bids/export")
    @Produces(CsvWriter.MEDIA_TYPE)
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Exporta a CSV (UTF-8 con BOM, separador ;, fechas de Lima) mis ofertas con los mismos datos que /my-bids",
            description = "Filtros opcionales: status (PENDING, ACCEPTED, LOST, CANCELLED, WITHDRAWN), originCity, destinationCity, "
                    + "fromDate y toDate (fecha de recojo, inclusive). «Neto estimado» usa la comisión vigente (ADR-0010). "
                    + "Máximo 10 000 filas: si hay más, LOGYX-EXP-001. Pasa por la misma consulta y RLS que el listado JSON.")
    public Response exportMyBids(@QueryParam("status") String status, @QueryParam("originCity") String originCity,
                                 @QueryParam("destinationCity") String destinationCity,
                                 @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
                                 @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate) {
        java.time.LocalDate from = MarketplaceFilter.date(fromDate, "fromDate"), to = MarketplaceFilter.date(toDate, "toDate");
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (wanted != null && !java.util.Set.of("PENDING", "ACCEPTED", "LOST", "CANCELLED", "WITHDRAWN").contains(wanted)) {
            throw pe.logyx.shared.domain.BusinessException.badRequest("LOGYX-VAL-001", "Estado inválido: " + status, "status");
        }
        var rows = queries.myBidSummaries().stream()
                .filter(s -> wanted == null || MarketplaceCsv.bidState(s).equals(wanted))
                .filter(s -> originCity == null || originCity.isBlank() || s.request().cargo().originCity().name().equalsIgnoreCase(originCity.trim()))
                .filter(s -> destinationCity == null || destinationCity.isBlank() || s.request().cargo().destinationCity().name().equalsIgnoreCase(destinationCity.trim()))
                .filter(s -> from == null || !s.request().cargo().pickupDate().isBefore(from))
                .filter(s -> to == null || !s.request().cargo().pickupDate().isAfter(to))
                .toList();
        if (rows.size() > MarketplaceQueries.EXPORT_MAX_ROWS) {
            throw pe.logyx.shared.domain.BusinessException.badRequest("LOGYX-EXP-001",
                    "Hay más de " + MarketplaceQueries.EXPORT_MAX_ROWS + " ofertas: acota por estado, ciudad o fechas", "status");
        }
        byte[] body = MarketplaceCsv.myBids(rows, amount -> commission.breakdown(amount).carrierNet());
        String name = "mis-ofertas-" + java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")) + ".csv";
        return Response.ok(body, CsvWriter.MEDIA_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").build();
    }

    @POST
    @Path("/shipment-requests/{id}/bids")
    @RolesAllowed(Roles.CARRIER)
    @ResponseStatus(201)
    public ApiResponse<BidView> bid(@PathParam("id") UUID id, @Valid PlaceBid req) {
        var placed = placeBid.placeBidWithWarnings(id, req.toCommand());
        return ApiResponse.of(BidView.of(placed.summary(),
                placed.warnings().stream().map(w -> new BidWarningView(w.code(), w.message())).toList()));
    }

    @GET
    @Path("/shipment-requests/{id}/vehicle-eligibility")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "RN02 por fecha: qué vehículos de mi flota pueden ofertar a esta solicitud",
            description = "Por vehículo: eligible, el impedimento (CAP-001 kg, 002 mantenimiento, 003 tipo, 004 m³, "
                    + "005 envío activo solapado, 007 demasiadas ofertas solapadas), desde cuándo está libre y su agenda "
                    + "en la ventana de la carga (envíos activos y ofertas pendientes, recojo + ⌈km/500⌉ días). "
                    + "warnings trae LOGYX-CAP-006 si ya tiene ofertas pendientes solapadas (se puede ofertar). "
                    + "La validación dura sigue al aceptar (CAP-005).")
    public ApiResponse<EligibilityView> eligibility(@PathParam("id") UUID id) {
        var e = queries.vehicleEligibility(id);
        return ApiResponse.of(new EligibilityView(e.window().from(), e.window().to(), e.maxOverlapping(),
                e.vehicles().stream().map(VehicleEligibilityView::of).toList()));
    }

    // ----- Común
    @GET
    @Path("/shipment-requests/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    public ApiResponse<RequestView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(RequestView.of(queries.detail(id)));
    }
}
