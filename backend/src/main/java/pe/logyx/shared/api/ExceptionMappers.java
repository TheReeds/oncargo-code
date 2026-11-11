package pe.logyx.shared.api;

import io.quarkus.logging.Log;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AuthRateLimits;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ExceptionMappers {

    @ServerExceptionMapper
    public Response business(BusinessException e) {
        var r = Response.status(e.status()).entity(ApiError.of(e.code(), e.getMessage(), e.field()));
        if (e instanceof AuthRateLimits.TooManyRequests t) r.header("Retry-After", t.retryAfterSeconds());
        return r.build();
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        var v = e.getConstraintViolations().stream().findFirst().orElse(null);
        String field = v == null ? null : lastNode(v.getPropertyPath().toString());
        String msg = v == null ? "Datos inválidos" : v.getMessage();
        return Response.status(400).entity(ApiError.of("LOGYX-VAL-001", msg, field)).build();
    }

    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response unauthorized(RuntimeException e) {
        return Response.status(401).entity(ApiError.of("LOGYX-AUTH-401", "No autenticado", null)).build();
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return Response.status(403).entity(ApiError.of("LOGYX-AUTH-403", "No tiene permisos para esta acción", null)).build();
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        return Response.status(status).entity(ApiError.of("LOGYX-HTTP-" + status, e.getMessage(), null)).build();
    }

    @ServerExceptionMapper
    public Response unexpected(Exception e) {
        Log.error("Error no controlado", e);
        return Response.status(500).entity(ApiError.of("LOGYX-GEN-500", "Error interno", null)).build();
    }

    private static String lastNode(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? path : path.substring(i + 1);
    }
}
