package pe.logyx.alerts.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.alerts.application.SavedSearchService;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Búsquedas guardadas del marketplace y avisos de carga nueva (roadmap 2.3). Cada persona ve solo las suyas (RLS).
 * Cuando se publica una carga que coincide y el vehículo puede ofertar, llega un aviso {@code NEW_LOAD_MATCH}
 * (in-app, tiempo real y correo según la categoría «Alertas de carga» de sus preferencias de aviso).
 */
@Path("/api/v1/saved-searches")
@RolesAllowed(Roles.CARRIER)
public class SavedSearchResource {

    @Inject SavedSearchService service;

    /** Filtros opcionales (al menos uno). {@code vehicleId}: solo avisa si ese vehículo puede ofertar. */
    public record SavedSearchRequest(
            @NotBlank @Size(max = SavedSearch.NAME_MAX) String name,
            City originCity, City destinationCity, CargoType cargoType,
            @Schema(type = SchemaType.STRING, format = "date") LocalDate pickupFrom,
            @Schema(type = SchemaType.STRING, format = "date") LocalDate pickupTo,
            @DecimalMin("1") BigDecimal maxWeightKg, UUID vehicleId,
            /** Al editar: activa o pausa los avisos. Al crear se ignora (nace activa). */
            Boolean active) {

        SavedSearch.Filters filters() {
            return new SavedSearch.Filters(name, originCity, destinationCity, cargoType, pickupFrom, pickupTo,
                    maxWeightKg, vehicleId);
        }
    }

    public record SavedSearchView(UUID id, String name, City originCity, City destinationCity, CargoType cargoType,
                                  @Schema(type = SchemaType.STRING, format = "date") LocalDate pickupFrom,
                                  @Schema(type = SchemaType.STRING, format = "date") LocalDate pickupTo,
                                  BigDecimal maxWeightKg, UUID vehicleId, boolean active, OffsetDateTime createdAt) {
        static SavedSearchView of(SavedSearch s) {
            return new SavedSearchView(s.id(), s.name(), s.originCity(), s.destinationCity(), s.cargoType(),
                    s.pickupFrom(), s.pickupTo(), s.maxWeightKg(), s.vehicleId(), s.active(), s.createdAt());
        }
    }

    @GET
    @Operation(summary = "Mis búsquedas guardadas (solo las de quien consulta), las nuevas primero")
    public ApiResponse<List<SavedSearchView>> mine() {
        return ApiResponse.of(service.mine().stream().map(SavedSearchView::of).toList());
    }

    @POST
    @ResponseStatus(201)
    @Operation(summary = "Guarda una búsqueda del marketplace para recibir avisos de cargas nuevas",
            description = "Nombre (máx. 80) y al menos un filtro (400 LOGYX-ALT-001); filtros incoherentes 400 LOGYX-ALT-003; "
                    + "tope por persona (409 LOGYX-ALT-002, 20 por omisión). Los canales del aviso se eligen en la categoría "
                    + "«Alertas de carga» de las preferencias de aviso. Máximo 10 avisos por búsqueda y hora.")
    public ApiResponse<SavedSearchView> create(@Valid SavedSearchRequest req) {
        return ApiResponse.of(SavedSearchView.of(service.create(req.filters())));
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Reemplaza el nombre y los filtros de una búsqueda; con active pausa o reanuda los avisos")
    public ApiResponse<SavedSearchView> update(@PathParam("id") UUID id, @Valid SavedSearchRequest req) {
        return ApiResponse.of(SavedSearchView.of(service.update(id, req.filters(), req.active())));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Borra una búsqueda guardada")
    public ApiResponse<Object> delete(@PathParam("id") UUID id) {
        service.delete(id);
        return ApiResponse.of(java.util.Map.of("id", id));
    }
}
