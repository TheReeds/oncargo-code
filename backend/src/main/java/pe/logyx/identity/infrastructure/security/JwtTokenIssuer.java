package pe.logyx.identity.infrastructure.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.port.out.TokenIssuer;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.util.Set;

/**
 * JWT RSA. Un conductor recibe solo el grupo DRIVER (no puede usar los endpoints
 * administrativos de su transportista); el resto, el tipo de su organización.
 */
@ApplicationScoped
public class JwtTokenIssuer implements TokenIssuer {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "logyx.jwt.ttl")
    Duration ttl;

    @Override
    public String issue(Profile p, Organization org) {
        Set<String> groups = p.isDriver() ? Set.of(Roles.DRIVER) : Set.of(org.type().name());
        return Jwt.issuer(issuer)
                .subject(p.id().toString())
                .upn(p.email())
                .groups(groups)
                .claim(TenantContext.CLAIM_ORG, org.id().toString())
                .claim(TenantContext.CLAIM_ORG_TYPE, org.type().name())
                .claim(TenantContext.CLAIM_ROLE, p.role().name())
                .expiresIn(ttl)
                .sign();
    }

    @Override
    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
