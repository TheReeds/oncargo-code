package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;

/** Emite el token de acceso (JWT) con los claims de tenancy (ver TenantContext). */
public interface TokenIssuer {
    String issue(Profile profile, Organization organization);
    long ttlSeconds();
}
