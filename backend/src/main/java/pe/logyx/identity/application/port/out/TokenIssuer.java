package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;

import java.time.Duration;

/** Emite el token de acceso (JWT) con los claims de tenancy (ver TenantContext) y la versión de sesión {@code sv}. */
public interface TokenIssuer {
    /** Token de la sesión web (sin refresh): {@code logyx.jwt.ttl}. */
    String issue(Profile profile, Organization organization);
    String issue(Profile profile, Organization organization, Duration ttl);
    long ttlSeconds();
    /** Vida del access token cuando la sesión usa refresh token (móvil): {@code logyx.jwt.refresh-access-ttl}. */
    Duration refreshAccessTtl();
}
