package pe.logyx.reports.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.reports.application.ReportService;
import pe.logyx.reports.infrastructure.web.ReportDtos.ReportOverviewView;
import pe.logyx.reports.infrastructure.web.ReportDtos.StatementView;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.api.CsvWriter;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Reportes y documentos internos (roadmap 2.11). El conductor no tiene acceso. */
@Path("/api/v1")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
public class ReportResource {

    @Inject ReportService reports;
    @Inject TenantContext tenant;

    @GET
    @Path("/reports/overview")
    @Operation(summary = "Reporte del período: PYME (gasto, ahorro, costo por kg), transportista (ingresos netos, "
            + "comisión, km, ocupación, adjudicación por ruta) u operador (GMV, comisión, envíos)",
            description = "Por fecha de adjudicación (Lima), envíos no cancelados, con precio final (acordado + ajustes "
                    + "aceptados), IGV y comisión con la tasa guardada en cada envío. Sin fechas: los últimos 6 meses. "
                    + "Rango máximo 400 días (LOGYX-VAL-001). La PYME no recibe la comisión.")
    public ApiResponse<ReportOverviewView> overview(
            @Parameter(description = "Desde (yyyy-MM-dd).") @QueryParam("from") @Schema(type = SchemaType.STRING, format = "date") String from,
            @Parameter(description = "Hasta (yyyy-MM-dd).") @QueryParam("to") @Schema(type = SchemaType.STRING, format = "date") String to) {
        return ApiResponse.of(ReportOverviewView.of(reports.overview(date(from, "from"), date(to, "to"))));
    }

    @GET
    @Path("/reports/export")
    @Produces(CsvWriter.MEDIA_TYPE)
    @Operation(summary = "Exporta a CSV (UTF-8 con BOM, separador ;) los envíos del reporte, uno por fila",
            description = "Mismos filtros y RLS que /reports/overview. Máximo 10 000 filas (LOGYX-EXP-001). Para el "
                    + "transportista y el operador incluye comisión, su IGV y el neto.")
    public Response export(@QueryParam("from") @Schema(type = SchemaType.STRING, format = "date") String from,
                           @QueryParam("to") @Schema(type = SchemaType.STRING, format = "date") String to) {
        var lines = reports.export(date(from, "from"), date(to, "to"));
        byte[] body = ReportCsv.of(lines, tenant.orgType() != OrgType.PYME);
        String name = "reporte-" + ReportService.today() + ".csv";
        return Response.ok(body, CsvWriter.MEDIA_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").build();
    }

    @GET
    @Path("/reports/statements/{period}")
    @Operation(summary = "Documento interno del mes (por fecha de entrega): liquidación de comisión (transportista) o "
            + "resumen de costos (PYME)",
            description = "period = yyyy-MM, no futuro. El operador indica organizationId. No es un comprobante SUNAT: la "
                    + "web lo muestra como página imprimible (Guardar como PDF).")
    public ApiResponse<StatementView> statement(@PathParam("period") String period,
                                                @Parameter(description = "Organización (solo el operador).")
                                                @QueryParam("organizationId") UUID organizationId) {
        return ApiResponse.of(StatementView.of(reports.statement(period, organizationId)));
    }

    private static LocalDate date(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Fecha inválida (yyyy-MM-dd)", field);
        }
    }
}
