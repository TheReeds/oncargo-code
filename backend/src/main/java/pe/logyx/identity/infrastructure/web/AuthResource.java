package pe.logyx.identity.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.infrastructure.web.AuthDtos.*;
import io.vertx.core.http.HttpServerRequest;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AuthRateLimits;

@Path("/api/v1/auth")
public class AuthResource {

    @Inject AuthService auth;
    @Inject AuthRateLimits limits;
    @Inject HttpServerRequest http;

    /** IP del cliente: la de la conexión o, detrás de un proxy confiable, la de X-Forwarded-For (quarkus.http.proxy). */
    private String clientIp() {
        var addr = http.remoteAddress();
        return addr == null ? "desconocida" : addr.hostAddress();
    }

    @POST
    @Path("/register")
    @ResponseStatus(201)
    public ApiResponse<AuthResponse> register(@Valid RegisterRequest req) {
        limits.beforeRegister(clientIp());
        return ApiResponse.of(AuthResponse.of(auth.register(req.toCommand())));
    }

    @POST
    @Path("/login")
    public ApiResponse<AuthResponse> login(@Valid LoginRequest req) {
        limits.beforeLogin(clientIp(), req.email());
        try {
            var result = auth.login(req.email(), req.password());
            limits.loginSucceeded(req.email());
            return ApiResponse.of(AuthResponse.of(result));
        } catch (BusinessException e) {
            if ("LOGYX-AUTH-001".equals(e.code())) limits.loginFailed(req.email());
            throw e;
        }
    }

    @GET
    @Path("/me")
    @Authenticated
    public ApiResponse<UserInfo> me() {
        return ApiResponse.of(UserInfo.of(auth.me()));
    }
}
