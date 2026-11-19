package pe.logyx.identity.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.OrganizationPreferencesService;
import pe.logyx.identity.domain.model.OrganizationPreferences;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Preferencias de la organización (V24, roadmap 2.2). Raíz {@code /api/v1/organizations} (la misma que
 * {@code OrganizationResource}: Quarkus REST no combina raíces que se solapan).
 */
@Path("/api/v1/organizations")
public class OrganizationPreferencesResource {

    /** Ruta de interés (ciudades del catálogo). */
    public record InterestRouteView(@NotNull City origin, @NotNull City destination) {}

    /**
     * Lo declarado por la organización. Transportista: {@code routeAlerts} y {@code minPricePerKm}. PYME:
     * {@code defaultPickupFrom/To} y {@code paymentTermsDays}. Lo que no aplica viene nulo ({@code routeAlerts}: true).
     */
    public record OrganizationPreferencesView(UUID organizationId, List<City> cities, List<InterestRouteView> routes,
                                              List<CargoType> cargoTypes, boolean routeAlerts, BigDecimal minPricePerKm,
                                              LocalTime defaultPickupFrom, LocalTime defaultPickupTo,
                                              Integer paymentTermsDays, OffsetDateTime updatedAt) {
        static OrganizationPreferencesView of(OrganizationPreferences p) {
            return new OrganizationPreferencesView(p.organizationId(), p.cities(),
                    p.routes().stream().map(r -> new InterestRouteView(r.origin(), r.destination())).toList(),
                    p.cargoTypes(), p.routeAlerts(), p.minPricePerKm(), p.defaultPickupFrom(), p.defaultPickupTo(),
                    p.paymentTermsDays(), p.updatedAt());
        }
    }

    public record UpdateOrganizationPreferences(
            @Size(max = 30) List<@NotNull City> cities,
            @Schema(description = "Hasta 20 rutas; origen y destino distintos (400 LOGYX-ORG-010/011)")
            @Size(max = 40) List<@Valid @NotNull InterestRouteView> routes,
            @Size(max = 20) List<@NotNull CargoType> cargoTypes,
            @Schema(description = "Transportista: avisarme de cargas nuevas en mis rutas de interés (por omisión true)")
            Boolean routeAlerts,
            @Schema(description = "Transportista: precio sugerido mínimo por km que me interesa (S/, opcional)")
            @DecimalMin("0.01") BigDecimal minPricePerKm,
            @Schema(description = "PYME: inicio del horario de recojo por defecto (HH:mm)") LocalTime defaultPickupFrom,
            @Schema(description = "PYME: fin del horario de recojo por defecto (HH:mm)") LocalTime defaultPickupTo,
            @Schema(description = "PYME: plazo de pago a sus transportistas en días (0–120)") @Min(0) @Max(120)
            Integer paymentTermsDays) {

        OrganizationPreferencesService.UpdateCommand toCommand() {
            return new OrganizationPreferencesService.UpdateCommand(cities,
                    routes == null ? List.of() : routes.stream()
                            .map(r -> new OrganizationPreferences.InterestRoute(r.origin(), r.destination())).toList(),
                    cargoTypes, routeAlerts, minPricePerKm, defaultPickupFrom, defaultPickupTo, paymentTermsDays);
        }
    }

    @Inject OrganizationPreferencesService service;

    @GET
    @Path("/me/preferences")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @Operation(summary = "Preferencias de mi organización (V24)", description = "Cualquier rol de la organización "
            + "(también Lector). Ciudades, rutas y tipos de carga de interés; del transportista, alertas por rutas y "
            + "precio mínimo por km; de la PYME, horario de recojo por defecto y plazo de pago.")
    public ApiResponse<OrganizationPreferencesView> mine() {
        return ApiResponse.of(OrganizationPreferencesView.of(service.mine()));
    }

    @PUT
    @Path("/me/preferences")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @OrgAdminOnly
    @Operation(summary = "Cambiar las preferencias de mi organización (V24)", description = "Solo ADMIN (403 "
            + "LOGYX-AUTH-008). Reemplaza todo; lo que no aplica al tipo de organización se ignora. 400 LOGYX-ORG-010 "
            + "(ruta con origen = destino), ORG-011 (más de 20 rutas), ORG-012 (horario de recojo), ORG-013 (plazo de "
            + "pago), ORG-014 (precio mínimo por km).")
    public ApiResponse<OrganizationPreferencesView> update(@Valid @NotNull UpdateOrganizationPreferences req) {
        return ApiResponse.of(OrganizationPreferencesView.of(service.update(req.toCommand())));
    }

    @GET
    @Path("/{id}/preferences")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Preferencias de una organización (operador, V24)", description = "Solo lectura.")
    public ApiResponse<OrganizationPreferencesView> of(@PathParam("id") UUID id) {
        return ApiResponse.of(OrganizationPreferencesView.of(service.of(id)));
    }
}
