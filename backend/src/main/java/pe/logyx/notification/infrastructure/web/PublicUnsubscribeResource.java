package pe.logyx.notification.infrastructure.web;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.resteasy.reactive.RestResponse;
import pe.logyx.notification.application.UnsubscribeService;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.security.AuthRateLimits;

/**
 * Baja del correo de avisos sin iniciar sesión (V24, 2.2; RFC 8058). Sin JWT. El token va en el cuerpo (no en la URL
 * del backend, que queda en los logs). Límite por IP (429 {@code LOGYX-AUTH-429}); token mal formado, alterado o de
 * una persona que ya no está: 404 {@code LOGYX-NTF-003} (misma respuesta). Solo la lista blanca {@link UnsubscribeView}.
 */
@Path("/api/v1/public/unsubscribe")
public class PublicUnsubscribeResource {

    public record UnsubscribeTokenRequest(@NotBlank @Size(max = 200) String token) {}

    /** {@code ALL} = todo el correo de avisos; {@code CATEGORY} = solo la categoría del enlace. */
    public enum UnsubscribeScope { CATEGORY, ALL }

    public record UnsubscribeRequest(@NotBlank @Size(max = 200) String token,
                                     @Schema(description = "CATEGORY (por omisión) o ALL") UnsubscribeScope scope) {}

    /**
     * Lo único que ve quien abre el enlace: la categoría (nula si el enlace es de todo el correo), el email enmascarado
     * y si esa categoría y el correo en general siguen activos.
     */
    public record UnsubscribeView(NotificationCategory category, String categoryLabel, String email,
                                  boolean categoryEmail, boolean emailEnabled) {
        static UnsubscribeView of(UnsubscribeService.Info i) {
            return new UnsubscribeView(i.category(), i.categoryLabel(), i.maskedEmail(), i.categoryEmail(), i.emailEnabled());
        }
    }

    @Inject UnsubscribeService service;
    @Inject AuthRateLimits limits;
    @Inject HttpServerRequest http;

    @POST
    @Path("/inspect")
    @Operation(summary = "Qué da de baja un enlace de correo (sin cuenta)", description = "Sin JWT. Categoría, email "
            + "enmascarado y estado actual. 404 LOGYX-NTF-003 si el enlace no es válido.")
    public RestResponse<ApiResponse<UnsubscribeView>> inspect(@Valid @NotNull UnsubscribeTokenRequest req) {
        limits.beforePublicUnsubscribe(clientIp());
        return noindex(UnsubscribeView.of(service.inspect(req.token())));
    }

    @POST
    @Operation(summary = "Darse de baja del correo de avisos (sin cuenta)", description = "Sin JWT. scope=CATEGORY apaga "
            + "el correo de la categoría del enlace (el aviso en la app sigue); ALL apaga todo el correo de avisos. "
            + "Idempotente. La web la usa también para el one-click de List-Unsubscribe (RFC 8058). 404 LOGYX-NTF-003.")
    public RestResponse<ApiResponse<UnsubscribeView>> unsubscribe(@Valid @NotNull UnsubscribeRequest req) {
        limits.beforePublicUnsubscribe(clientIp());
        return noindex(UnsubscribeView.of(service.unsubscribe(req.token(), req.scope() == UnsubscribeScope.ALL)));
    }

    private static RestResponse<ApiResponse<UnsubscribeView>> noindex(UnsubscribeView v) {
        return RestResponse.ResponseBuilder.ok(ApiResponse.of(v)).header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer").build();
    }

    private String clientIp() {
        var addr = http.remoteAddress();
        return addr == null ? "desconocida" : addr.hostAddress();
    }
}
