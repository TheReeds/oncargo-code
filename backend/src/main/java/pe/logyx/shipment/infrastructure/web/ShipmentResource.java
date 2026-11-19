package pe.logyx.shipment.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shipment.application.AssignDriverUseCase;
import pe.logyx.shipment.application.DriverShipmentScope;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.TrackStopsUseCase;
import pe.logyx.shipment.application.ShipmentLifecycleUseCase;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.CancelShipment;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.ChangeVehicle;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.AssignDriver;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.ShipmentView;
import pe.logyx.shipment.infrastructure.web.ShipmentDtos.StopEvent;
import pe.logyx.shipment.infrastructure.web.ShareLinkDtos.CreateShareLink;
import pe.logyx.shipment.infrastructure.web.ShareLinkDtos.ShareLinkCreated;
import pe.logyx.shipment.infrastructure.web.ShareLinkDtos.ShareLinkView;
import pe.logyx.shipment.application.ShareLinkService;
import pe.logyx.shipment.application.ShipmentFilter;
import pe.logyx.shared.api.CsvWriter;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.resteasy.reactive.ResponseStatus;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class ShipmentResource {

    @Inject ShipmentQueries queries;
    @Inject AssignDriverUseCase assignDriver;
    @Inject TrackStopsUseCase tracking;
    @Inject ShipmentLifecycleUseCase lifecycle;
    @Inject ShareLinkService shareLinks;
    @Inject TenantContext tenant;
    @ConfigProperty(name = "logyx.web.url") String webUrl;

    @GET
    @Path("/shipments")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @Operation(summary = "Mis envíos (PYME o transportista), recientes primero, con filtros en el servidor",
            description = "Filtros opcionales: status (uno o varios separados por coma, p. ej. PENDING,IN_TRANSIT), "
                    + "originCity, destinationCity, fromDate/toDate (fecha de recojo, yyyy-MM-dd, inclusive) y q "
                    + "(código OC-…, referencia propia, placa, conductor o carga). Con page/size: meta.total. Sin "
                    + "page ni size devuelve todos los que cumplan (compatibilidad; tope 10 000).")
    public ApiResponse<List<ShipmentView>> mine(
            @Parameter(description = "Página 0-based. Si se omite page y size, se devuelven todos.")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20 cuando se pagina).")
            @QueryParam("size") Integer size,
            @QueryParam("status") String status, @QueryParam("originCity") String originCity,
            @QueryParam("destinationCity") String destinationCity,
            @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
            @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
            @QueryParam("q") String q) {
        ShipmentFilter filter = ShipmentFilter.of(status, originCity, destinationCity, fromDate, toDate, q);
        if (page == null && size == null) {
            return ApiResponse.of(ShipmentView.of(filter.isEmpty() ? queries.mine() : queries.exportMine(filter)));
        }
        return ApiResponse.of(queries.mine(filter, PageRequest.of(page, size)).map(ShipmentView::of));
    }

    @GET
    @Path("/shipments/export")
    @Produces(CsvWriter.MEDIA_TYPE)
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @Operation(summary = "Exporta a CSV (UTF-8 con BOM, separador ;, fechas de Lima) los envíos que cumplen los mismos "
            + "filtros que GET /shipments. Máximo 10 000 filas: si hay más, LOGYX-EXP-001")
    public Response export(@QueryParam("status") String status, @QueryParam("originCity") String originCity,
                           @QueryParam("destinationCity") String destinationCity,
                           @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
                           @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
                           @QueryParam("q") String q) {
        ShipmentFilter filter = ShipmentFilter.of(status, originCity, destinationCity, fromDate, toDate, q);
        byte[] body = ShipmentCsv.shipments(queries.exportMine(filter), tenant.orgType() == OrgType.CARRIER);
        String name = "envios-" + LocalDate.now(ZoneId.of("America/Lima")) + ".csv";
        return Response.ok(body, CsvWriter.MEDIA_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").build();
    }

    @GET
    @Path("/shipments/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    public ApiResponse<ShipmentView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(ShipmentView.of(queries.detail(id)));
    }

    @PUT
    @Path("/shipments/{id}/driver")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Asigna o releva el conductor (motivo obligatorio si ya se recogió; sin envíos solapados, "
            + "LOGYX-SHP-004)")
    public ApiResponse<ShipmentView> assign(@PathParam("id") UUID id, @Valid AssignDriver req) {
        return ApiResponse.of(ShipmentView.of(assignDriver.assign(id, req.driverId(), req.reason())));
    }

    @PUT
    @Path("/shipments/{id}/vehicle")
    @RolesAllowed(Roles.CARRIER)
    @Operation(summary = "Cambia el vehículo del envío (RN02: kg, tipo y m³; sin envíos solapados, LOGYX-CAP-005; "
            + "motivo obligatorio si ya se recogió)")
    public ApiResponse<ShipmentView> changeVehicle(@PathParam("id") UUID id, @Valid ChangeVehicle req) {
        return ApiResponse.of(ShipmentView.of(lifecycle.changeVehicle(id, req.vehicleId(), req.reason())));
    }

    @POST
    @Path("/shipments/{id}/cancel")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    @Operation(summary = "Cancela el envío con motivo: las partes antes del recojo (LOGYX-SHP-003), el operador también "
            + "en tránsito. La solicitud queda cancelada y se avisa a la contraparte")
    public ApiResponse<ShipmentView> cancel(@PathParam("id") UUID id, @Valid @NotNull CancelShipment req) {
        return ApiResponse.of(ShipmentView.of(lifecycle.cancel(id, req.reason())));
    }

    // ----- App del conductor
    @GET
    @Path("/driver/shipments")
    @RolesAllowed(Roles.DRIVER)
    @Operation(summary = "Mis envíos como conductor: activos (por omisión), historial o todos (F22)")
    public ApiResponse<List<ShipmentView>> driverShipments(
            @Parameter(description = "ACTIVE (por omisión: pendientes y en tránsito), HISTORY (entregados y cancelados, "
                    + "recientes primero) o ALL (activos y luego historial).") @QueryParam("scope") String scope) {
        return ApiResponse.of(ShipmentView.of(queries.driverShipments(DriverShipmentScope.parse(scope))));
    }

    @GET
    @Path("/driver/shipments/active")
    @RolesAllowed(Roles.DRIVER)
    public ApiResponse<ShipmentView> driverActive() {
        return ApiResponse.of(ShipmentView.of(queries.activeForDriver()));
    }

    @POST
    @Path("/shipments/{id}/stops/{stopId}/arrive")
    @RolesAllowed(Roles.DRIVER)
    @Operation(summary = "«Llegué» a la parada (RN08, en orden). Idempotente: repetirlo responde 200 con el envío",
            description = "Cuerpo opcional {occurredAt}: hora del dispositivo (ISO-8601 con zona). Debe ser como mucho 2 min "
                    + "futura, de las últimas 48 h y no anterior al paso previo; si no, 400 LOGYX-TRK-008 (field "
                    + "occurredAt; la app reintenta sin ella). Sin cuerpo vale la hora del servidor.")
    public ApiResponse<ShipmentView> arrive(@PathParam("id") UUID id, @PathParam("stopId") UUID stopId,
                                            StopEvent body) {
        return ApiResponse.of(ShipmentView.of(tracking.arrive(id, stopId, body == null ? null : body.occurredAt())));
    }

    @POST
    @Path("/shipments/{id}/stops/{stopId}/complete")
    @RolesAllowed(Roles.DRIVER)
    @Operation(summary = "Completa la parada («Carga recogida» / «Entregado», con foto en la entrega). Idempotente",
            description = "Repetirlo sobre una parada ya completada responde 200 con el envío (aunque ya esté entregado). "
                    + "Cuerpo opcional {occurredAt} con las mismas reglas que arrive (LOGYX-TRK-008).")
    public ApiResponse<ShipmentView> complete(@PathParam("id") UUID id, @PathParam("stopId") UUID stopId,
                                              StopEvent body) {
        return ApiResponse.of(ShipmentView.of(tracking.complete(id, stopId, body == null ? null : body.occurredAt())));
    }

    // ----- Enlaces públicos de seguimiento (roadmap 1.6, ADR-0011)
    @POST
    @Path("/shipments/{id}/share-links")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Crea un enlace público de seguimiento para el cliente final (ADMIN y OPS de la PYME dueña)",
            description = "El token y la URL se devuelven UNA sola vez (en la BD solo queda su SHA-256). Errores: "
                    + "LOGYX-SHL-002 envío cancelado, LOGYX-SHL-003 entregado hace más de 7 días, LOGYX-SHL-004 ya hay "
                    + "10 vigentes, LOGYX-VAL-001 expiresInDays fuera de 1–30.")
    @ResponseStatus(201)
    public ApiResponse<ShareLinkCreated> createShareLink(@PathParam("id") UUID id,
                                                                     @Valid CreateShareLink req) {
        var created = shareLinks.create(id, req == null ? null : req.expiresInDays());
        String url = webUrl.replaceAll("/+$", "") + "/s/" + created.token();
        return ApiResponse.of(new ShareLinkCreated(ShareLinkView.of(created.summary()), created.token(), url));
    }

    @GET
    @Path("/shipments/{id}/share-links")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Enlaces públicos del envío (sin el token): estado, caducidad, visitas. También el Lector")
    public ApiResponse<List<ShareLinkView>> shareLinks(@PathParam("id") UUID id) {
        return ApiResponse.of(shareLinks.list(id).stream().map(ShareLinkView::of).toList());
    }

    @POST
    @Path("/shipments/{id}/share-links/{linkId}/revoke")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Revoca un enlace público: deja de funcionar al instante (idempotente)")
    public ApiResponse<ShareLinkView> revokeShareLink(@PathParam("id") UUID id, @PathParam("linkId") UUID linkId) {
        return ApiResponse.of(ShareLinkView.of(shareLinks.revoke(id, linkId)));
    }
}
