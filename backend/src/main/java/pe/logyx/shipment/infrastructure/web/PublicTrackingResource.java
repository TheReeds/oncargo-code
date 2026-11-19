package pe.logyx.shipment.infrastructure.web;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestResponse;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AuthRateLimits;
import pe.logyx.shipment.application.PublicTrackingService;
import pe.logyx.shipment.infrastructure.web.ShareLinkDtos.PublicTrackingView;

/**
 * Seguimiento público por enlace (roadmap 1.6, ADR-0011). Sin JWT. Límite por IP (429 {@code LOGYX-AUTH-429}).
 * Inexistente, mal formado, revocado o vencido responden lo mismo: 404 {@code LOGYX-SHL-001}. {@code noindex} y sin
 * referer: el token va en la URL.
 */
@Path("/api/v1/public/tracking")
public class PublicTrackingResource {

    @Inject PublicTrackingService tracking;
    @Inject AuthRateLimits limits;
    @Inject HttpServerRequest http;

    @GET
    @Path("/{token}")
    @Operation(summary = "Seguimiento público de un envío por su enlace (sin cuenta)",
            description = "Sin JWT. Solo estado, ciudades, fecha de recojo, hitos con hora, ETA aproximada (ventana) y "
                    + "ruta por carretera: nunca precio, contactos, direcciones, placa, conductor ni datos de las "
                    + "organizaciones. 404 LOGYX-SHL-001 si el enlace no existe, se revocó o venció (misma respuesta). "
                    + "Límite por IP: 429 LOGYX-AUTH-429 con Retry-After.")
    public RestResponse<ApiResponse<PublicTrackingView>> view(@PathParam("token") String token) {
        limits.beforePublicTracking(clientIp());
        PublicTrackingView view = tracking.resolve(token).map(PublicTrackingView::of)
                .orElseThrow(() -> new BusinessException(404, "LOGYX-SHL-001", "Este enlace ya no está disponible"));
        return RestResponse.ResponseBuilder.ok(ApiResponse.of(view))
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private String clientIp() {
        var addr = http.remoteAddress();
        return addr == null ? "desconocida" : addr.hostAddress();
    }
}
