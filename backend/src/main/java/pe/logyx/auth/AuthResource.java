package pe.logyx.auth;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.auth.AuthDtos.*;
import pe.logyx.shared.api.ApiResponse;

@Path("/api/v1/auth")
public class AuthResource {

    @Inject AuthService auth;

    @POST
    @Path("/register")
    @ResponseStatus(201)
    public ApiResponse<AuthResponse> register(@Valid RegisterRequest req) {
        return ApiResponse.of(auth.register(req));
    }

    @POST
    @Path("/login")
    public ApiResponse<AuthResponse> login(@Valid LoginRequest req) {
        return ApiResponse.of(auth.login(req));
    }

    @GET
    @Path("/me")
    @Authenticated
    public ApiResponse<UserInfo> me() {
        return ApiResponse.of(auth.me());
    }
}
