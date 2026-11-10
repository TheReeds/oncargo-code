package pe.logyx.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.organization.Profile;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "logyx.jwt.ttl")
    Duration ttl;

    public String issue(Profile p) {
        Set<String> groups = p.role == Profile.Role.DRIVER
                ? Set.of(Roles.DRIVER)
                : Set.of(p.organization.type.name());
        return Jwt.issuer(issuer)
                .subject(p.id.toString())
                .upn(p.email)
                .groups(groups)
                .claim(TenantContext.CLAIM_ORG, p.organization.id.toString())
                .claim(TenantContext.CLAIM_ORG_TYPE, p.organization.type.name())
                .claim(TenantContext.CLAIM_ROLE, p.role.name())
                .expiresIn(ttl)
                .sign();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
