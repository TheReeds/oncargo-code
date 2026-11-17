package pe.logyx.identity.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.identity.application.AuthResult;
import pe.logyx.identity.application.RegisterCommand;
import pe.logyx.identity.application.UserAccount;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.OrgType;

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
            @AssertTrue(message = "Debe aceptar la política de privacidad (Ley N° 29733)") boolean acceptPrivacyPolicy) {

        RegisterCommand toCommand() {
            return new RegisterCommand(organizationType, legalName, ruc, city, phone, fullName, email, password);
        }
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    /**
     * {@code rucVerified}: sello "Verificado" (RF08), lo otorga el operador LOGYX (antes bastaba el dígito
     * verificador). {@code rucValid}: el RUC pasó el módulo 11. {@code verificationStatus}/{@code verificationNote}:
     * estado de la revisión y motivo si fue rechazada o suspendida (RN01).
     */
    public record UserInfo(UUID id, String email, String fullName, Profile.Role role,
                           UUID organizationId, String organizationName, OrgType organizationType,
                           boolean rucVerified, String organizationCity, boolean rucValid,
                           Organization.VerificationStatus verificationStatus, String verificationNote) {
        static UserInfo of(UserAccount a) {
            var p = a.profile();
            var o = a.organization();
            return new UserInfo(p.id(), p.email(), p.fullName(), p.role(), o.id(), o.legalName(), o.type(),
                    o.rucVerified(), o.city(), o.rucValid(), o.verificationStatus(), o.verification().note());
        }
    }

    public record AuthResponse(String accessToken, long expiresIn, UserInfo user) {
        static AuthResponse of(AuthResult r) {
            return new AuthResponse(r.accessToken(), r.expiresIn(), UserInfo.of(r.user()));
        }
    }
}
