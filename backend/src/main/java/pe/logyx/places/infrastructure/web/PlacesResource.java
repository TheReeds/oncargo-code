package pe.logyx.places.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.places.application.PlaceService;
import pe.logyx.places.infrastructure.web.PlaceDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

/** Libreta de direcciones y almacenes frecuentes de la PYME (ADMIN y OPS escriben; VIEWER solo lee). */
@Path("/api/v1")
public class PlacesResource {

    @Inject PlaceService places;

    @GET
    @Path("/places")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Direcciones guardadas de la organización: la usada más recientemente primero")
    public ApiResponse<List<PlaceView>> list(
            @Parameter(description = "Código de ciudad (p. ej. LIMA).") @QueryParam("city") String city,
            @Parameter(description = "PICKUP, DELIVERY o BOTH: PICKUP/DELIVERY incluyen las que sirven para ambos.")
            @QueryParam("kind") String kind,
            @Parameter(description = "Texto en el nombre, la dirección o el contacto.") @QueryParam("q") String q,
            @Parameter(description = "Incluye las archivadas (por omisión no).")
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived) {
        return ApiResponse.of(places.list(city, kind, q, includeArchived).stream().map(PlaceView::of).toList());
    }

    @GET
    @Path("/places/{id}")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<PlaceView> get(@PathParam("id") UUID id) {
        return ApiResponse.of(PlaceView.of(places.get(id)));
    }

    @POST
    @Path("/places")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    public ApiResponse<PlaceView> create(@Valid PlaceRequest req) {
        return ApiResponse.of(PlaceView.of(places.create(req.toData())));
    }

    @PATCH
    @Path("/places/{id}")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Edita una dirección activa (reemplaza los campos editables); archivada: LOGYX-PLC-002")
    public ApiResponse<PlaceView> update(@PathParam("id") UUID id, @Valid PlaceRequest req) {
        return ApiResponse.of(PlaceView.of(places.update(id, req.toData())));
    }

    @POST
    @Path("/places/{id}/archive")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Archiva la dirección (no se borra; deja de ofrecerse al publicar)")
    public ApiResponse<PlaceView> archive(@PathParam("id") UUID id) {
        return ApiResponse.of(PlaceView.of(places.archive(id)));
    }

    @POST
    @Path("/places/{id}/restore")
    @RolesAllowed(Roles.PYME)
    @Operation(summary = "Restaura una dirección archivada")
    public ApiResponse<PlaceView> restore(@PathParam("id") UUID id) {
        return ApiResponse.of(PlaceView.of(places.restore(id)));
    }
}
