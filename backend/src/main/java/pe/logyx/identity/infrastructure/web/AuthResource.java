package pe.logyx.identity.infrastructure.web;

import io.quarkus.security.Authenticated;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.identity.application.AuthService;
import pe.logyx.identity.application.PasswordTokenService;
import pe.logyx.identity.application.RefreshSessions;
import pe.logyx.identity.infrastructure.web.AuthDtos.*;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AuthRateLimits;
import pe.logyx.shared.tenancy.TenantContext;

@Path("/api/v1/auth")
public class AuthResource {

    /** Mismo cuerpo exista o no el correo (no revela cuentas). */
    static final String FORGOT_MESSAGE = "Si el correo está registrado, te enviamos un enlace para restablecer tu "
            + "contraseña. Revisa tu bandeja de entrada y la carpeta de spam.";

    @Inject AuthService auth;
    @Inject PasswordTokenService passwordTokens;
    @Inject RefreshSessions refreshSessions;
    @Inject pe.logyx.identity.application.TermsService terms;
    @Inject AuthRateLimits limits;
    @Inject TenantContext tenant;
    @Inject HttpServerRequest http;
    @Inject pe.logyx.identity.application.SignupFingerprints signupFingerprints;

    /** IP del cliente: la de la conexión o, detrás de un proxy confiable, la de X-Forwarded-For (quarkus.http.proxy). */
    private String clientIp() {
        var addr = http.remoteAddress();
        return addr == null ? "desconocida" : addr.hostAddress();
    }

    private String userAgent() {
        return http.getHeader("User-Agent");
    }

    @POST
    @Path("/register")
    @ResponseStatus(201)
    public ApiResponse<AuthResponse> register(@Valid RegisterRequest req) {
        limits.beforeRegister(clientIp());
        var result = auth.register(req.toCommand());
        signupFingerprints.record(result.user().organization().id(), clientIp());   // 2.15: señales de fraude
        return ApiResponse.of(AuthResponse.of(result, terms.currentVersion()));
    }

    @POST
    @Path("/login")
    @Operation(summary = "Iniciar sesión", description = "Con refresh=true (app móvil) la respuesta trae refreshToken "
            + "y el access token dura 1 h; renuévalo con POST /auth/refresh.")
    public ApiResponse<AuthResponse> login(@Valid LoginRequest req) {
        limits.beforeLogin(clientIp(), req.email());
        try {
            var result = auth.login(req.email(), req.password(), req.wantsRefresh(), userAgent());
            limits.loginSucceeded(req.email());
            return ApiResponse.of(AuthResponse.of(result, terms.currentVersion()));
        } catch (BusinessException e) {
            if ("LOGYX-AUTH-001".equals(e.code())) limits.loginFailed(req.email());
            throw e;
        }
    }

    @GET
    @Path("/me")
    @Authenticated
    public ApiResponse<UserInfo> me() {
        return ApiResponse.of(UserInfo.of(auth.me(), terms.currentVersion()));
    }

    // ------------------------------------------------------------ Contraseñas (F02)

    @POST
    @Path("/forgot-password")
    @ResponseStatus(202)
    @Operation(summary = "Olvidé mi contraseña", description = "Siempre 202 con el mismo mensaje, exista o no el "
            + "correo. Si existe, llega un enlace de un solo uso (30 min). 429 LOGYX-AUTH-429 por IP.")
    public ApiResponse<AuthMessage> forgotPassword(@Valid ForgotPasswordRequest req) {
        limits.beforeForgotPassword(clientIp());
        if (limits.forgotPasswordEmailAllowed(req.email())) passwordTokens.requestResetAsync(req.email());
        return ApiResponse.of(new AuthMessage(FORGOT_MESSAGE));
    }

    @POST
    @Path("/password-tokens/inspect")
    @Operation(summary = "Ver un enlace de contraseña", description = "Propósito (RESET o INVITE), vencimiento, email "
            + "enmascarado y organización. 400 LOGYX-AUTH-005 si no vale.")
    public ApiResponse<PasswordTokenInfo> inspectToken(@Valid PasswordTokenRequest req) {
        limits.beforePasswordTokenUse(clientIp());
        return ApiResponse.of(PasswordTokenInfo.of(passwordTokens.inspect(req.token())));
    }

    @POST
    @Path("/reset-password")
    @Operation(summary = "Definir contraseña con el enlace", description = "Restablecer o aceptar una invitación. "
            + "Cierra todas las sesiones del usuario. 400 LOGYX-AUTH-005 (token inválido, vencido o usado), "
            + "LOGYX-AUTH-011 (invitación sin aceptar la política de privacidad), LOGYX-AUTH-013 (invitación sin aceptar "
            + "los Términos, V24); 409 LOGYX-AUTH-012 (versión de los Términos desactualizada).")
    public ApiResponse<AuthMessage> resetPassword(@Valid ResetPasswordRequest req) {
        limits.beforePasswordTokenUse(clientIp());
        var r = passwordTokens.redeem(req.token(), req.newPassword(), Boolean.TRUE.equals(req.acceptPrivacyPolicy()),
                Boolean.TRUE.equals(req.acceptTerms()), req.termsVersion());
        return ApiResponse.of(new AuthMessage(r.purpose() == pe.logyx.identity.domain.model.PasswordTokenPurpose.INVITE
                ? "Tu cuenta está activa. Ya puedes iniciar sesión."
                : "Listo, tu contraseña cambió. Inicia sesión con la nueva."));
    }

    @POST
    @Path("/change-password")
    @Authenticated
    @Operation(summary = "Cambiar mi contraseña", description = "Cualquier rol. Cierra las demás sesiones y devuelve "
            + "una sesión nueva (reemplaza el token). 400 LOGYX-AUTH-006 (actual incorrecta), LOGYX-AUTH-010 (igual).")
    public ApiResponse<AuthResponse> changePassword(@Valid ChangePasswordRequest req) {
        String key = tenant.profileId().toString();
        limits.beforeChangePassword(key);
        try {
            return ApiResponse.of(AuthResponse.of(auth.changePassword(req.currentPassword(), req.newPassword(),
                    req.wantsRefresh(), userAgent()), terms.currentVersion()));
        } catch (BusinessException e) {
            if ("LOGYX-AUTH-006".equals(e.code())) limits.changePasswordFailed(key);
            throw e;
        }
    }

    // ------------------------------------------------------------ Términos (V24, 2.17)

    @POST
    @Path("/terms/accept")
    @Authenticated
    @Operation(summary = "Aceptar la versión vigente de los Términos y condiciones", description = "Cualquier rol "
            + "(también Lector). Cuando cambia la versión, GET /auth/me trae termsAccepted=false y la web lo pide. "
            + "Idempotente. 409 LOGYX-AUTH-012 si termsVersion no es la vigente.")
    public ApiResponse<TermsStatus> acceptTerms(@Valid AcceptTermsRequest req) {
        return ApiResponse.of(new TermsStatus(terms.accept(req.termsVersion()), true));
    }

    // ------------------------------------------------------------ Sesiones (F24, F55)

    @POST
    @Path("/refresh")
    @Operation(summary = "Renovar la sesión (app móvil)", description = "Rota el refresh token: devuelve access + "
            + "refresh nuevos; el anterior deja de valer. Reutilizar uno ya usado cierra esa sesión. 401 LOGYX-AUTH-009. "
            + "No envíes Authorization.")
    public ApiResponse<AuthResponse> refresh(@Valid RefreshRequest req) {
        limits.beforeRefresh(clientIp());
        return ApiResponse.of(AuthResponse.of(refreshSessions.rotate(req.refreshToken()), terms.currentVersion()));
    }

    @POST
    @Path("/logout")
    @ResponseStatus(204)
    @Operation(summary = "Cerrar sesión en este dispositivo", description = "Con refreshToken revoca esa sesión. "
            + "El access token de la web no se revoca (vence solo); para eso usa /auth/logout-all.")
    public void logout(@Valid LogoutRequest req) {
        if (req != null && req.refreshToken() != null) refreshSessions.logout(req.refreshToken());
    }

    @POST
    @Path("/logout-all")
    @Authenticated
    @ResponseStatus(204)
    @Operation(summary = "Cerrar sesión en todos los dispositivos", description = "Invalida todos los tokens del "
            + "usuario, incluido el actual.")
    public void logoutAll() {
        auth.logoutAll();
    }
}
