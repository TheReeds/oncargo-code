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
import pe.logyx.shared.api.ApiResponse;

@Path("/api/v1/auth")
public class AuthResource {

    @Inject AuthService auth;

    @POST
    @Path("/register")
    @ResponseStatus(201)
    public ApiResponse<AuthResponse> register(@Valid RegisterRequest req) {
        return ApiResponse.of(AuthResponse.of(auth.register(req.toCommand())));
    }

    @POST
    @Path("/login")
    public ApiResponse<AuthResponse> login(@Valid LoginRequest req) {
        return ApiResponse.of(AuthResponse.of(auth.login(req.email(), req.password())));
    }

    @GET
    @Path("/me")
    @Authenticated
    public ApiResponse<UserInfo> me() {
        return ApiResponse.of(UserInfo.of(auth.me()));
    }
}
