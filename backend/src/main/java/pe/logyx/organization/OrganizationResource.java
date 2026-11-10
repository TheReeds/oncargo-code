package pe.logyx.organization;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.api.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

import java.util.UUID;

@Path("/api/v1/organizations")
@Authenticated
public class OrganizationResource {

    /** Perfil público verificado de una organización (sin datos personales). */
    public record PublicOrganization(UUID id, OrgType type, String legalName, String ruc,
                                     boolean rucVerified, String city) {
        static PublicOrganization of(Organization o) {
            return new PublicOrganization(o.id, o.type, o.legalName, o.ruc, o.rucVerified(), o.city);
        }
    }

    @GET
    @Path("/{id}")
    @Transactional
    public ApiResponse<PublicOrganization> get(@PathParam("id") UUID id) {
        Organization o = Organization.<Organization>findByIdOptional(id)
                .orElseThrow(() -> BusinessException.notFound("Organización"));
        return ApiResponse.of(PublicOrganization.of(o));
    }
}
