package pe.logyx.identity.infrastructure.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pe.logyx.identity.application.OrganizationDirectory;
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

    @GET
    @Path("/{id}")
    public ApiResponse<PublicOrganization> get(@PathParam("id") UUID id) {
        return ApiResponse.of(PublicOrganization.of(directory.get(id)));
    }
}
