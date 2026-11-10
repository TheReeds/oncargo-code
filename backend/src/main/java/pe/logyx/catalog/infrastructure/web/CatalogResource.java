package pe.logyx.catalog.infrastructure.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.api.ApiResponse;

import java.util.Arrays;
import java.util.List;

/** Catálogos públicos para poblar formularios en web y móvil. */
@Path("/api/v1/catalog")
public class CatalogResource {

    public record CityItem(String code, String name, double lat, double lng) {}
    public record CargoItem(String code, String name) {}

    @GET
    @Path("/cities")
    public ApiResponse<List<CityItem>> cities() {
        return ApiResponse.of(Arrays.stream(City.values())
                .map(c -> new CityItem(c.name(), c.label, c.lat, c.lng)).toList());
    }

    @GET
    @Path("/cargo-types")
    public ApiResponse<List<CargoItem>> cargoTypes() {
        return ApiResponse.of(Arrays.stream(CargoType.values())
                .map(c -> new CargoItem(c.name(), c.label)).toList());
    }
}
