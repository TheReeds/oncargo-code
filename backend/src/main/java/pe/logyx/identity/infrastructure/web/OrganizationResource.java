package pe.logyx.identity.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.Operation;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.application.OrganizationProfileService;
import pe.logyx.identity.infrastructure.web.OrganizationDtos.OrganizationProfile;
import pe.logyx.identity.infrastructure.web.OrganizationDtos.UpdateOrganizationRequest;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.tenancy.OrgType;

import java.util.UUID;

@Path("/api/v1/organizations")
@Authenticated
public class OrganizationResource {

    /**
     * Perfil público de una organización (sin datos personales). {@code rucVerified} = sello "Verificado" que otorga
     * el operador (RF08); {@code rucValid} = el RUC pasó el dígito verificador.
     */
    public record PublicOrganization(UUID id, OrgType type, String legalName, String ruc,
                                     boolean rucVerified, String city, boolean rucValid,
                                     Organization.VerificationStatus verificationStatus) {
        static PublicOrganization of(Organization o) {
            return new PublicOrganization(o.id(), o.type(), o.legalName(), o.ruc(), o.rucVerified(), o.city(),
                    o.rucValid(), o.verificationStatus());
        }
    }

    @Inject OrganizationDirectory directory;
    @Inject OrganizationProfileService profile;

    @GET
    @Path("/me")
    @Operation(summary = "Perfil de mi organización", description = "Datos completos de la propia organización "
            + "(teléfono y dirección no son públicos). Cualquier rol.")
    public ApiResponse<OrganizationProfile> mine() {
        return ApiResponse.of(OrganizationProfile.of(profile.mine()));
    }

    @PATCH
    @Path("/me")
    @OrgAdminOnly
    @Operation(summary = "Editar el perfil de mi organización", description = "Solo ADMIN (403 LOGYX-AUTH-008). "
            + "Ciudad, teléfono y dirección siempre; razón social y RUC solo con verificación PENDING o REJECTED "
            + "(409 LOGYX-ORG-007). Si estaba REJECTED vuelve a PENDING y se avisa a LOGYX.")
    public ApiResponse<OrganizationProfile> update(@Valid UpdateOrganizationRequest req) {
        return ApiResponse.of(OrganizationProfile.of(profile.update(req.toCommand())));
    }

    @GET
    @Path("/{id}")
    public ApiResponse<PublicOrganization> get(@PathParam("id") UUID id) {
        return ApiResponse.of(PublicOrganization.of(directory.get(id)));
    }
}
