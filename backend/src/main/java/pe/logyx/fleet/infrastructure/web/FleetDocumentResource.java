package pe.logyx.fleet.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import pe.logyx.fleet.application.FleetDocumentService;
import pe.logyx.fleet.domain.FleetDocumentPolicy;
import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.Roles;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Documentos de flota con vencimiento (2.4): privados del transportista; ver {@code FleetDocumentService}. */
@Path("/api/v1")
@RolesAllowed(Roles.CARRIER)
public class FleetDocumentResource {

    @Inject FleetDocumentService service;

    /**
     * {@code status}: VALID, EXPIRING (faltan 30 días o menos), EXPIRED o NO_EXPIRY; {@code daysLeft} negativo si ya
     * venció. {@code current=false}: lo reemplazó uno más nuevo (historial).
     */
    public record FleetDocumentView(UUID id, FleetDocumentType type, String typeLabel, String ownerType, UUID ownerId,
                                    String number, @Schema(type = SchemaType.STRING, format = "date") LocalDate expiresAt,
                                    FleetDocumentPolicy.Vigency status, Long daysLeft, String fileName,
                                    String contentType, long sizeBytes, java.time.OffsetDateTime createdAt,
                                    boolean current) {
        static FleetDocumentView of(FleetDocument d) {
            LocalDate today = FleetDocumentService.today();
            return new FleetDocumentView(d.id(), d.type(), d.type().label(), d.type().owner().name(), d.ownerId(),
                    d.number(), d.expiresAt(), FleetDocumentPolicy.status(d.expiresAt(), today),
                    d.expiresAt() == null ? null : ChronoUnit.DAYS.between(today, d.expiresAt()), d.fileName(),
                    d.contentType(), d.sizeBytes(), d.createdAt(), d.isCurrent());
        }
    }

    @GET
    @Path("/vehicles/{id}/documents")
    @Operation(summary = "Documentos del vehículo (SOAT, revisión técnica, MTC…): vigentes e historial")
    public ApiResponse<List<FleetDocumentView>> ofVehicle(@PathParam("id") UUID id) {
        return ApiResponse.of(service.ofVehicle(id).stream().map(FleetDocumentView::of).toList());
    }

    @GET
    @Path("/drivers/{id}/documents")
    @Operation(summary = "Documentos del conductor (licencia, DNI): vigentes e historial")
    public ApiResponse<List<FleetDocumentView>> ofDriver(@PathParam("id") UUID id) {
        return ApiResponse.of(service.ofDriver(id).stream().map(FleetDocumentView::of).toList());
    }

    @GET
    @Path("/fleet/documents")
    @Operation(summary = "Documentos vigentes de toda mi flota, por vencimiento (tablero de vencimientos)")
    public ApiResponse<List<FleetDocumentView>> current() {
        return ApiResponse.of(service.currentOfMine().stream()
                .sorted(java.util.Comparator.comparing(FleetDocument::expiresAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(FleetDocumentView::of).toList());
    }

    @POST
    @Path("/vehicles/{id}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    @Operation(summary = "Sube un documento del vehículo (multipart: file, type, number, expiresAt)",
            description = "type: SOAT, TECHNICAL_INSPECTION, PROPERTY_CARD, INSURANCE_POLICY o MTC_PERMIT. Los que tienen "
                    + "vencimiento exigen expiresAt (400 LOGYX-FLT-010). JPG, PNG o PDF. Reemplaza al vigente del mismo tipo "
                    + "(queda en el historial). Avisos a 30 y 7 días y el día que vence.")
    public ApiResponse<FleetDocumentView> uploadVehicle(
            @PathParam("id") UUID id,
            @RestForm("file") @Schema(type = SchemaType.STRING, format = "binary") FileUpload file,
            @RestForm("type") @Schema(enumeration = {"SOAT", "TECHNICAL_INSPECTION", "PROPERTY_CARD", "INSURANCE_POLICY",
                    "MTC_PERMIT"}) String type,
            @RestForm("number") String number,
            @RestForm("expiresAt") @Schema(type = SchemaType.STRING, format = "date") String expiresAt) {
        return upload(id, FleetDocumentType.Owner.VEHICLE, file, type, number, expiresAt);
    }

    @POST
    @Path("/drivers/{id}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    @Operation(summary = "Sube un documento del conductor (multipart: file, type, number, expiresAt)",
            description = "type: DRIVER_LICENSE (exige expiresAt) o DNI. Subir la licencia actualiza licenseExpiresAt del conductor.")
    public ApiResponse<FleetDocumentView> uploadDriver(
            @PathParam("id") UUID id,
            @RestForm("file") @Schema(type = SchemaType.STRING, format = "binary") FileUpload file,
            @RestForm("type") @Schema(enumeration = {"DRIVER_LICENSE", "DNI"}) String type,
            @RestForm("number") String number,
            @RestForm("expiresAt") @Schema(type = SchemaType.STRING, format = "date") String expiresAt) {
        return upload(id, FleetDocumentType.Owner.DRIVER, file, type, number, expiresAt);
    }

    private ApiResponse<FleetDocumentView> upload(UUID ownerId, FleetDocumentType.Owner owner, FileUpload file,
                                                  String type, String number, String expiresAt) {
        if (file == null || file.uploadedFile() == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Adjunta un archivo", "file");
        }
        FleetDocumentType t = parseType(type, owner);
        var cmd = new FleetDocumentService.Upload(t, ownerId, number, parseDate(expiresAt), file.fileName(),
                file.size(), () -> Files.newInputStream(file.uploadedFile()));
        return ApiResponse.of(FleetDocumentView.of(service.upload(cmd)));
    }

    @GET
    @Path("/fleet/documents/{id}/file")
    @Produces({"image/jpeg", "image/png", "application/pdf"})
    @Operation(summary = "Descarga el archivo de un documento de flota (solo el transportista dueño)")
    @APIResponse(responseCode = "200", description = "Contenido del archivo",
            content = @Content(schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public Response download(@PathParam("id") UUID id) {
        var d = service.download(id);
        FleetDocument doc = d.document();
        String ascii = doc.fileName().replaceAll("[^\\x20-\\x7E]", "").replace("\"", "");
        String utf8 = URLEncoder.encode(doc.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return Response.ok(d.content(), doc.contentType())
                .header("Content-Disposition", "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8)
                .header("Content-Length", doc.sizeBytes())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .build();
    }

    private static FleetDocumentType parseType(String type, FleetDocumentType.Owner owner) {
        if (type == null || type.isBlank()) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el tipo de documento", "type");
        }
        return Arrays.stream(FleetDocumentType.values())
                .filter(t -> t.owner() == owner && t.name().equalsIgnoreCase(type.trim())).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Tipo de documento inválido", "type"));
    }

    private static LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Fecha inválida (yyyy-MM-dd)", "expiresAt");
        }
    }
}
