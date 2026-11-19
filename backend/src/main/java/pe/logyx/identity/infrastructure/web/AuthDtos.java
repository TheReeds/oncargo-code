package pe.logyx.identity.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.identity.application.AuthResult;
import pe.logyx.identity.application.RegisterCommand;
import pe.logyx.identity.application.UserAccount;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import pe.logyx.identity.application.PasswordTokenService;
import pe.logyx.identity.domain.OrgAccessPolicy;
import pe.logyx.identity.domain.OrgPermission;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.PasswordTokenPurpose;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotNull(message = "El tipo de organización es obligatorio") OrgType organizationType,
            @NotBlank(message = "La razón social es obligatoria") @Size(max = 200) String legalName,
            @NotBlank(message = "El RUC es obligatorio") @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos") String ruc,
            @Size(max = 80) String city,
            @Size(max = 20) String phone,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String fullName,
            @NotBlank @Email(message = "Email inválido") String email,
            @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,
            @AssertTrue(message = "Debe aceptar la política de privacidad (Ley N° 29733)") boolean acceptPrivacyPolicy,
            @AssertTrue(message = "Debe aceptar los Términos y condiciones") boolean acceptTerms,
            @Schema(description = "Versión de los Términos y condiciones que el usuario leyó (la de GET /public/legal). "
                    + "Si se omite se asume la vigente; si no es la vigente, 409 LOGYX-AUTH-012")
            @Size(max = 40) String termsVersion) {

        RegisterCommand toCommand() {
            return new RegisterCommand(organizationType, legalName, ruc, city, phone, fullName, email, password,
                    termsVersion == null || termsVersion.isBlank() ? "" : termsVersion.trim());
        }
    }

    /**
     * {@code refresh=true} (opt-in, app móvil/conductor): la respuesta trae {@code refreshToken} y el access token
     * dura {@code logyx.jwt.refresh-access-ttl} (1 h). La web no lo usa (sesión de 8 h revocable).
     */
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password,
                               @Schema(description = "Pedir refresh token rotativo (app móvil). Por omisión false")
                               Boolean refresh) {
        boolean wantsRefresh() { return Boolean.TRUE.equals(refresh); }
    }

    public record ForgotPasswordRequest(@NotBlank @Email(message = "Email inválido") @Size(max = 254) String email) {}

    /**
     * Canje de un token de contraseña (restablecer o invitación). {@code acceptPrivacyPolicy} y {@code acceptTerms}
     * son obligatorios (true) solo para una invitación ({@code LOGYX-AUTH-011}, {@code LOGYX-AUTH-013}).
     */
    public record ResetPasswordRequest(
            @NotBlank(message = "Falta el token del enlace") @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String newPassword,
            Boolean acceptPrivacyPolicy,
            @Schema(description = "V24: obligatorio (true) para una invitación (LOGYX-AUTH-013)") Boolean acceptTerms,
            @Schema(description = "V24: versión de los Términos que leyó (la de GET /public/legal). Si se omite se asume "
                    + "la vigente; si no es la vigente, 409 LOGYX-AUTH-012") @Size(max = 40) String termsVersion) {}

    /** V24 (2.17): aceptar la versión vigente de los Términos y condiciones (la de {@code UserInfo.termsVersion}). */
    public record AcceptTermsRequest(@NotBlank(message = "Falta la versión de los términos") @Size(max = 40) String termsVersion) {}

    /** Estado de los términos de la persona tras aceptarlos. */
    public record TermsStatus(String termsVersion, boolean termsAccepted) {}

    public record PasswordTokenRequest(@NotBlank(message = "Falta el token del enlace") @Size(max = 200) String token) {}

    /** Lo que muestra la pantalla del enlace antes de pedir la contraseña. {@code email} viene enmascarado. */
    public record PasswordTokenInfo(PasswordTokenPurpose purpose, OffsetDateTime expiresAt, String email,
                                    String organizationName) {
        static PasswordTokenInfo of(PasswordTokenService.TokenInfo i) {
            return new PasswordTokenInfo(i.purpose(), i.expiresAt(), i.maskedEmail(), i.organizationName());
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Ingresa tu contraseña actual") @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String newPassword,
            @Schema(description = "Emitir también un refresh token (app móvil). Por omisión false") Boolean refresh) {
        boolean wantsRefresh() { return Boolean.TRUE.equals(refresh); }
    }

    public record RefreshRequest(@NotBlank(message = "Falta el refresh token") @Size(max = 200) String refreshToken) {}

    /** Body opcional de {@code POST /auth/logout}: con {@code refreshToken} se revoca esa sesión del dispositivo. */
    public record LogoutRequest(@Size(max = 200) String refreshToken) {}

    /** Respuesta de texto para acciones sin datos (p. ej. "Si el correo está registrado…"). */
    public record AuthMessage(String message) {}

    /**
     * {@code rucVerified}: sello "Verificado" (RF08), lo otorga el operador LOGYX (antes bastaba el dígito
     * verificador). {@code rucValid}: el RUC pasó el módulo 11. {@code verificationStatus}/{@code verificationNote}:
     * estado de la revisión y motivo si fue rechazada o suspendida (RN01).
     */
    public record UserInfo(UUID id, String email, String fullName, Profile.Role role,
                           UUID organizationId, String organizationName, OrgType organizationType,
                           boolean rucVerified, String organizationCity, boolean rucValid,
                           Organization.VerificationStatus verificationStatus, String verificationNote,
                           @Schema(description = "Permisos por rol de organización (V14): READ, OPERATE, MANAGE_USERS, "
                                   + "MANAGE_ORGANIZATION, MANAGE_DRIVERS. Úsalos para mostrar u ocultar acciones; "
                                   + "el backend los hace cumplir igual")
                           List<OrgPermission> permissions,
                           @Schema(description = "V24 (2.17): la persona aceptó la versión vigente de los Términos. Si es "
                                   + "false, la web pide aceptarlos (POST /auth/terms/accept); a conductores y operadores "
                                   + "no se les pide")
                           boolean termsAccepted,
                           @Schema(description = "V24: versión vigente de los Términos y condiciones")
                           String termsVersion) {
        static UserInfo of(UserAccount a, String termsVersion) {
            var p = a.profile();
            var o = a.organization();
            return new UserInfo(p.id(), p.email(), p.fullName(), p.role(), o.id(), o.legalName(), o.type(),
                    o.rucVerified(), o.city(), o.rucValid(), o.verificationStatus(), o.verification().note(),
                    OrgAccessPolicy.permissions(p.role(), o.type()).stream().sorted().toList(), a.termsAccepted(),
                    termsVersion);
        }
    }

    /**
     * {@code refreshToken}/{@code refreshExpiresIn} (V14) solo vienen con {@code refresh=true}; si no, null.
     * {@code refreshExpiresIn} = segundos hasta el vencimiento absoluto de la sesión del dispositivo (30 días).
     */
    public record AuthResponse(String accessToken, long expiresIn, UserInfo user, String refreshToken,
                               Long refreshExpiresIn) {
        static AuthResponse of(AuthResult r, String termsVersion) {
            return new AuthResponse(r.accessToken(), r.expiresIn(), UserInfo.of(r.user(), termsVersion), r.refreshToken(),
                    r.refreshExpiresIn());
        }
    }
}
