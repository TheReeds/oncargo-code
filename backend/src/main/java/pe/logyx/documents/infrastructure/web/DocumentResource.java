package pe.logyx.documents.infrastructure.web;

import pe.logyx.documents.domain.model.DocumentType;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import pe.logyx.documents.application.DocumentQueries;
import pe.logyx.documents.application.DocumentService;
import pe.logyx.documents.application.UploadDocumentCommand;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.documents.infrastructure.web.DocumentDtos.DocumentView;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Path("/api/v1")
public class DocumentResource {

    @Inject DocumentService service;
    @Inject DocumentQueries queries;

    @POST
    @Path("/shipments/{id}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER})
    @ResponseStatus(201)
    @Operation(summary = "Sube un documento o evidencia de entrega (multipart: file, type, stopId)",
            description = "JPG, PNG o PDF (por firma binaria), máx. logyx.documents.max-size-bytes (10 MB). "
                    + "DELIVERY_PHOTO/SIGNATURE: solo imágenes, solo transportista/conductor, stopId de la parada de "
                    + "entrega ya con llegada registrada. Completar la entrega exige al menos una DELIVERY_PHOTO.")
    public ApiResponse<DocumentView> upload(
            @PathParam("id") UUID shipmentId,
            @RestForm("file") @Schema(type = SchemaType.STRING, format = "binary") FileUpload file,
            @RestForm("type") @Schema(enumeration = {"SERVICE_ORDER", "REMISSION_GUIDE", "RECEIPT", "DELIVERY_PHOTO",
                    "SIGNATURE", "OTHER"}) String type,
            @RestForm("stopId") @Schema(format = "uuid") String stopId) {
        if (file == null || file.uploadedFile() == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Adjunta un archivo", "file");
        }
        var cmd = new UploadDocumentCommand(shipmentId, parseType(type), parseUuid(stopId), file.fileName(),
                file.size(), () -> Files.newInputStream(file.uploadedFile()));
        return ApiResponse.of(DocumentView.of(service.upload(cmd)));
    }

    @GET
    @Path("/shipments/{id}/documents")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    public ApiResponse<List<DocumentView>> byShipment(@PathParam("id") UUID shipmentId) {
        return ApiResponse.of(queries.byShipment(shipmentId).stream().map(DocumentView::of).toList());
    }

    @GET
    @Path("/documents")
    @RolesAllowed({Roles.PYME, Roles.CARRIER})
    @Operation(summary = "Documentos de todos los envíos de mi organización (RF48), recientes primero")
    public ApiResponse<List<DocumentView>> mine(
            @Parameter(description = "Filtra por tipo de documento.") @QueryParam("type") String type,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        DocumentType t = type == null || type.isBlank() ? null : parseType(type);
        return ApiResponse.of(queries.mine(t, PageRequest.of(page, size)).map(DocumentView::of));
    }

    @GET
    @Path("/documents/{id}/file")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
    @Produces({"image/jpeg", "image/png", "application/pdf"})
    @Operation(summary = "Descarga el archivo (solo las partes del envío y el operador)")
    @APIResponse(responseCode = "200", description = "Contenido del archivo",
            content = @Content(schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public Response download(@PathParam("id") UUID id) {
        var d = queries.download(id);
        Document doc = d.document();
        return Response.ok(d.content(), doc.contentType())
                .header("Content-Disposition", disposition(doc.fileName()))
                .header("Content-Length", doc.sizeBytes())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .build();
    }

    /** {@code inline}, con nombre ASCII de respaldo y el original en UTF-8 (RFC 6266). */
    private static String disposition(String fileName) {
        String ascii = Normalizer.normalize(fileName, Normalizer.Form.NFD).replaceAll("[^\\x20-\\x7E]", "")
                .replace("\"", "");
        String utf8 = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8;
    }

    private static DocumentType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el tipo de documento", "type");
        }
        return Arrays.stream(DocumentType.values()).filter(t -> t.name().equalsIgnoreCase(type.trim())).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Tipo de documento inválido", "type"));
    }

    private static UUID parseUuid(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "stopId inválido", "stopId");
        }
    }
}
