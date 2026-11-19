package pe.logyx.search.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.search.application.SearchService;
import pe.logyx.search.domain.SearchHit;
import pe.logyx.search.domain.SearchHitType;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.Roles;

import java.util.List;
import java.util.UUID;

/** Búsqueda global (Ctrl+K). Cualquier usuario de PYME, transportista u operador; el conductor no. */
@Path("/api/v1")
public class SearchResource {

    @Inject SearchService search;

    public record SearchResultView(SearchHitType type, UUID id, String code, String title, String subtitle, String href) {
        static SearchResultView of(SearchHit h) {
            return new SearchResultView(h.type(), h.id(), h.code(), h.title(), h.subtitle(), h.href());
        }
    }

    @GET
    @Path("/search")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    @Operation(summary = "Busca solicitudes, envíos y contrapartes por código (OC-7K2QX), referencia propia, ruta, "
            + "placa o (operador) RUC o razón social. Solo devuelve lo que el usuario ya puede ver (RLS); hasta 8 por tipo")
    public ApiResponse<List<SearchResultView>> search(
            @Parameter(description = "Texto, de 2 a 80 caracteres.") @QueryParam("q") String q,
            @Parameter(description = "Tipos separados por coma: REQUEST, SHIPMENT, COUNTERPART (por omisión todos).")
            @QueryParam("types") String types) {
        return ApiResponse.of(search.search(q, types).stream().map(SearchResultView::of).toList());
    }
}
