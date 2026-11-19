package pe.logyx.catalog.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.catalog.application.CalendarService;
import pe.logyx.catalog.domain.City;
import pe.logyx.catalog.domain.model.CalendarDay;
import pe.logyx.catalog.domain.model.CalendarDayKind;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.Roles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Calendario de feriados y paros (roadmap 2.14, V24). Misma raíz exacta que {@link CatalogResource}. */
@Path("/api/v1/catalog")
public class CalendarResource {

    /** Un día del calendario. {@code cities} vacío = todo el país. */
    public record CalendarDayView(UUID id, LocalDate day, CalendarDayKind kind, String kindLabel, String name,
                                  List<String> cities, boolean national, boolean skipRecurrence, String note,
                                  OffsetDateTime createdAt) {
        static CalendarDayView of(CalendarDay d) {
            return new CalendarDayView(d.id(), d.day(), d.kind(), d.kind().label, d.name(),
                    d.cities().stream().map(Enum::name).sorted().toList(), d.national(), d.skipRecurrence(), d.note(),
                    d.createdAt());
        }
    }

    /** Alta de un día (operador). {@code cities}: códigos del catálogo; vacío o nulo = nacional. */
    public record CreateCalendarDay(@NotNull(message = "Elige la fecha") LocalDate day,
                                    @NotNull(message = "Elige el tipo") CalendarDayKind kind,
                                    @NotBlank(message = "Indica el nombre") @Size(max = 120) String name,
                                    List<String> cities, Boolean skipRecurrence, @Size(max = 300) String note) {}

    @Inject CalendarService calendar;

    @GET
    @Path("/calendar")
    @Authenticated
    @Operation(summary = "Feriados, paros y avisos entre dos fechas (inclusive)",
            description = "Cualquier rol autenticado (banner, aviso al elegir la fecha de recojo). Sin fechas: de hoy a "
                    + "60 días. Rango máximo 550 días (LOGYX-VAL-001). city filtra lo que afecta a esa ciudad (y lo nacional).")
    public ApiResponse<List<CalendarDayView>> list(
            @Parameter(description = "Desde (yyyy-MM-dd), por omisión hoy en Lima.") @QueryParam("from") String from,
            @Parameter(description = "Hasta (yyyy-MM-dd), por omisión desde + 60 días.") @QueryParam("to") String to,
            @Parameter(description = "Código de ciudad del catálogo (p. ej. PUNO).") @QueryParam("city") String city) {
        LocalDate f = date(from, "from", LocalDate.now(CalendarService.LIMA));
        LocalDate t = date(to, "to", f.plusDays(60));
        City c = city == null || city.isBlank() ? null : City.require(city);
        return ApiResponse.of(calendar.between(f, t).stream().filter(d -> c == null || d.affects(c))
                .map(CalendarDayView::of).toList());
    }

    @POST
    @Path("/calendar")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Agrega un feriado, paro o aviso (operador)",
            description = "LOGYX-CAL-001 fecha pasada o a más de 3 años (day), CAL-002 (409) ya existe ese día con ese "
                    + "nombre, VAL-001 datos inválidos, NEG-001 datos de contacto (RN07). Queda en la auditoría.")
    public ApiResponse<CalendarDayView> add(@Valid @NotNull CreateCalendarDay req) {
        Set<City> cities = EnumSet.noneOf(City.class);
        if (req.cities() != null) req.cities().forEach(code -> cities.add(City.require(code)));
        return ApiResponse.of(CalendarDayView.of(calendar.add(new CalendarService.NewCalendarDay(req.day(), req.kind(),
                req.name(), cities, req.skipRecurrence() == null || req.skipRecurrence(), req.note()))));
    }

    @DELETE
    @Path("/calendar/{id}")
    @RolesAllowed(Roles.OPERATOR)
    @Operation(summary = "Quita un día del calendario (operador), con motivo", description = "404 si no existe.")
    public ApiResponse<Boolean> remove(@PathParam("id") UUID id,
                                       @Parameter(description = "Motivo (3-300 caracteres).") @QueryParam("reason") String reason) {
        calendar.remove(id, reason);
        return ApiResponse.of(Boolean.TRUE);
    }

    private static LocalDate date(String raw, String field, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Fecha inválida (yyyy-MM-dd)", field);
        }
    }
}
