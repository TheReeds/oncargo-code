package pe.logyx.identity.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import pe.logyx.identity.application.UpdateOrganizationCommand;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Perfil de la propia organización (F20) y sus usuarios (F03). */
public final class OrganizationDtos {
    private OrganizationDtos() {}

    /**
     * Perfil completo de la propia organización (no es el público). {@code legalDataEditable}: razón social y RUC
     * se pueden corregir (verificación PENDING o REJECTED).
     */
    public record OrganizationProfile(UUID id, OrgType type, String legalName, String ruc, boolean rucValid,
                                      boolean rucVerified, Organization.VerificationStatus verificationStatus,
                                      String verificationNote, String city, String phone, String address,
                                      boolean legalDataEditable, OffsetDateTime createdAt) {
        static OrganizationProfile of(Organization o) {
            return new OrganizationProfile(o.id(), o.type(), o.legalName(), o.ruc(), o.rucValid(), o.rucVerified(),
                    o.verificationStatus(), o.verification().note(), o.city(), o.phone(), o.address(),
                    o.legalDataEditable(), o.createdAt());
        }
    }

    /** Campos ausentes (null) no cambian; "" borra ciudad, teléfono o dirección. */
    public record UpdateOrganizationRequest(
            @Schema(description = "Solo si la verificación está PENDING o REJECTED (si no, 409 LOGYX-ORG-007)")
            @Size(min = 2, max = 200, message = "La razón social debe tener entre 2 y 200 caracteres") String legalName,
            @Schema(description = "Solo si la verificación está PENDING o REJECTED (si no, 409 LOGYX-ORG-007)")
            @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos") String ruc,
            @Size(max = 80) String city,
            @Size(max = 20) @Pattern(regexp = "^$|^[0-9+()\\s-]{6,20}$", message = "Teléfono inválido") String phone,
            @Size(max = 200) String address) {
        UpdateOrganizationCommand toCommand() {
            return new UpdateOrganizationCommand(legalName, ruc, city, phone, address);
        }
    }

    /** Usuario de la organización (sin conductores). {@code invitationPending}: invitado que aún no definió su contraseña. */
    public record OrganizationUser(UUID id, String email, String fullName, Profile.Role role, boolean active,
                                   boolean invitationPending, OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
        static OrganizationUser of(Profile p) {
            return new OrganizationUser(p.id(), p.email(), p.fullName(), p.role(), p.active(), p.invitationPending(),
                    p.createdAt(), p.lastLoginAt());
        }
    }

    public record InviteUserRequest(
            @NotBlank @Email(message = "Email inválido") @Size(max = 254) String email,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String fullName,
            @Schema(description = "ADMIN, OPS o VIEWER (DRIVER no: los conductores se crean en la flota)")
            @NotNull(message = "Elige un rol") Profile.Role role) {}

    public record ChangeUserRoleRequest(
            @Schema(description = "ADMIN, OPS o VIEWER") @NotNull(message = "Elige un rol") Profile.Role role) {}
}
