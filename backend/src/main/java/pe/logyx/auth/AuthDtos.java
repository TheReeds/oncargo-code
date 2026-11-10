package pe.logyx.auth;

import jakarta.validation.constraints.*;
import pe.logyx.organization.Profile;
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
            @AssertTrue(message = "Debe aceptar la política de privacidad (Ley N° 29733)") boolean acceptPrivacyPolicy) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserInfo(UUID id, String email, String fullName, Profile.Role role,
                           UUID organizationId, String organizationName, OrgType organizationType,
                           boolean rucVerified) {
        public static UserInfo of(Profile p) {
            var o = p.organization;
            return new UserInfo(p.id, p.email, p.fullName, p.role, o.id, o.legalName, o.type, o.rucVerified());
        }
    }

    public record AuthResponse(String accessToken, long expiresIn, UserInfo user) {}
}
