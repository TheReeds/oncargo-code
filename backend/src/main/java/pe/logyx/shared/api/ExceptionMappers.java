package pe.logyx.shared.api;

import io.quarkus.logging.Log;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AuthRateLimits;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ExceptionMappers {

    @ServerExceptionMapper
    public Response business(BusinessException e) {
        var r = Response.status(e.status()).type(MediaType.APPLICATION_JSON)
                .entity(ApiError.of(e.code(), e.getMessage(), e.field()));
        if (e instanceof AuthRateLimits.TooManyRequests t) r.header("Retry-After", t.retryAfterSeconds());
        return r.build();
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        var v = e.getConstraintViolations().stream().findFirst().orElse(null);
        String field = v == null ? null : lastNode(v.getPropertyPath().toString());
        String msg = v == null ? "Datos inválidos" : v.getMessage();
        return json(400, ApiError.of("LOGYX-VAL-001", msg, field));
    }

    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response unauthorized(RuntimeException e) {
        return json(401, ApiError.of("LOGYX-AUTH-401", "No autenticado", null));
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return json(403, ApiError.of("LOGYX-AUTH-403", "No tiene permisos para esta acción", null));
    }

    /**
     * Errores del framework (ruta inexistente, método o formato no admitido…): código estable {@code LOGYX-HTTP-<status>}
     * y mensaje en español (F44); el mensaje de la excepción viene en inglés y no se muestra.
     */
    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        return json(status, ApiError.of("LOGYX-HTTP-" + status, httpMessage(status), null));
    }

    static String httpMessage(int status) {
        return switch (status) {
            case 400 -> "La solicitud no es válida";
            case 404 -> "No existe el recurso solicitado";
            case 405 -> "Ese método no está permitido en este recurso";
            case 406 -> "No podemos responder en el formato pedido";
            case 408 -> "La solicitud tardó demasiado";
            case 413 -> "El archivo o el contenido enviado es demasiado grande";
            case 415 -> "Ese tipo de contenido no está permitido";
            case 429 -> "Demasiadas solicitudes. Intenta nuevamente en unos minutos";
            default -> status >= 500 ? "Error interno" : "No pudimos procesar la solicitud";
        };
    }

    /**
     * Siempre JSON, aunque el endpoint produzca otro tipo (p. ej. la descarga de documentos con {@code Accept: *}/*):
     * sin esto el cuerpo de error salía como el {@code toString()} de Java (F45).
     */
    private static Response json(int status, ApiError body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    @ServerExceptionMapper
    public Response unexpected(Exception e) {
        Log.error("Error no controlado", e);
        return json(500, ApiError.of("LOGYX-GEN-500", "Error interno", null));
    }

    private static String lastNode(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? path : path.substring(i + 1);
    }
}
