package pe.logyx.pricing.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.pricing.application.TariffService;
import pe.logyx.pricing.domain.model.Tariff;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tarifario versionado (roadmap 2.14, V24). Misma raíz exacta que {@link PricingResource}. */
@Path("/api/v1/pricing")
public class TariffResource {

    /** Valores de una versión del tarifario; {@code cargoMultipliers} por código de {@code CargoType}. */
    public record TariffValues(BigDecimal baseFee, BigDecimal fuelPerKm, BigDecimal ratePerKmTon, BigDecimal tollEveryKm,
                               BigDecimal tollPrice, BigDecimal handlingRate, Map<String, BigDecimal> cargoMultipliers) {
        static TariffValues of(Tariff t) {
            Map<String, BigDecimal> m = new LinkedHashMap<>();
            for (CargoType c : CargoType.values()) m.put(c.name(), t.multiplier(c));
            return new TariffValues(t.baseFee(), t.fuelPerKm(), t.ratePerKmTon(), t.tollEveryKm(), t.tollPrice(),
                    t.handlingRate(), m);
        }
    }

    /**
     * Una versión del tarifario. {@code state}: {@code CURRENT} (rige), {@code SCHEDULED} (programada), {@code PAST}
     * (reemplazada) o {@code CANCELLED} (programada y anulada).
     */
    public record TariffVersionView(int version, String state, OffsetDateTime effectiveFrom, TariffValues values,
                                    String reason, UUID createdBy, String createdByName, OffsetDateTime createdAt,
                                    OffsetDateTime cancelledAt) {
        static TariffVersionView of(TariffService.TariffVersionInfo i) {
            var r = i.record();
            return new TariffVersionView(r.tariff().version(), i.state().name(), r.tariff().effectiveFrom(),
                    TariffValues.of(r.tariff()), r.reason(), r.createdBy(), r.createdByName(), r.createdAt(),
                    r.cancelledAt());
        }
    }

    /** Tarifa vigente, para mostrar con qué valores se cotiza. */
    public record CurrentTariff(int version, OffsetDateTime effectiveFrom, TariffValues values) {}

    /**
     * Versión nueva. {@code effectiveFrom} nulo = desde ya; futuro = programada (hasta un año). {@code reason} obligatorio.
     * {@code expectedLatestVersion}: la última versión que el operador veía (409 {@code LOGYX-TRF-005} si cambió).
     */
    public record CreateTariffVersion(@NotNull(message = "Indica la tarifa base") BigDecimal baseFee,
                                      @NotNull(message = "Indica el combustible por km") BigDecimal fuelPerKm,
                                      @NotNull(message = "Indica la tarifa por t·km") BigDecimal ratePerKmTon,
                                      @NotNull(message = "Indica cada cuántos km hay un peaje") BigDecimal tollEveryKm,
                                      @NotNull(message = "Indica el precio del peaje") BigDecimal tollPrice,
                                      @NotNull(message = "Indica el manipuleo") BigDecimal handlingRate,
                                      @NotNull(message = "Indica los multiplicadores por tipo de carga")
                                      Map<String, BigDecimal> cargoMultipliers,
                                      OffsetDateTime effectiveFrom,
                                      @NotBlank(message = "Indica el motivo del cambio") @Size(min = 3, max = 300) String reason,
                                      @Min(0) Integer expectedLatestVersion) {}

    public record CancelTariffVersion(@NotBlank(message = "Indica el motivo") @Size(min = 3, max = 300) String reason) {}

    @Inject TariffService tariffs;

    @GET
    @Path("/tariffs/current")
    @Authenticated
    @Operation(summary = "Tarifa vigente del motor de costos (versión, vigencia y valores)",
            description = "Cualquier rol autenticado. Sugerido = base + km × combustible + km × t × tarifa t·km × "
                    + "multiplicador(tipo) + ⌈km / tramo⌉ × peaje + manipuleo × transporte.")
    public ApiResponse<CurrentTariff> current() {
        Tariff t = tariffs.current();
        return ApiResponse.of(new CurrentTariff(t.version(), t.effectiveFrom(), TariffValues.of(t)));
    }

    @GET
    @Path("/tariffs")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Versiones del tarifario (operador), la más nueva primero")
    public ApiResponse<List<TariffVersionView>> versions() {
        return ApiResponse.of(tariffs.versions().stream().map(TariffVersionView::of).toList());
    }

    @POST
    @Path("/tariffs")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Crea una versión del tarifario, vigente ya o programada (operador)",
            description = "LOGYX-TRF-001 valor fuera de rango (field = el campo, p. ej. cargoMultipliers.FRAGILE), "
                    + "TRF-002 sin motivo, TRF-003 vigencia en el pasado o a más de un año, TRF-005 (409) otro operador "
                    + "creó una versión. Las solicitudes ya publicadas conservan su precio; las nuevas guardan la versión.")
    public ApiResponse<TariffVersionView> create(@Valid @NotNull CreateTariffVersion req) {
        Map<CargoType, BigDecimal> m = new EnumMap<>(CargoType.class);
        req.cargoMultipliers().forEach((k, v) -> {
            CargoType c;
            try {
                c = CargoType.valueOf(k);
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("LOGYX-TRF-001", "Tipo de carga desconocido: " + k, "cargoMultipliers");
            }
            m.put(c, v);
        });
        return ApiResponse.of(TariffVersionView.of(tariffs.create(new TariffService.NewTariff(req.baseFee(),
                req.fuelPerKm(), req.ratePerKmTon(), req.tollEveryKm(), req.tollPrice(), req.handlingRate(), m,
                req.effectiveFrom(), req.reason(), req.expectedLatestVersion()))));
    }

    @POST
    @Path("/tariffs/{version}/cancel")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Anula una versión programada que todavía no rige (operador)",
            description = "LOGYX-TRF-004 (409) si ya rige o ya estaba anulada; TRF-002 sin motivo.")
    public ApiResponse<TariffVersionView> cancel(@PathParam("version") int version, @Valid @NotNull CancelTariffVersion req) {
        tariffs.cancel(version, req.reason());
        return ApiResponse.of(TariffVersionView.of(tariffs.version(version)
                .orElseThrow(() -> BusinessException.notFound("Versión del tarifario"))));
    }
}
