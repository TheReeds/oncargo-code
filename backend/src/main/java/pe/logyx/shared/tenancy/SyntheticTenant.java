package pe.logyx.shared.tenancy;

import io.quarkus.arc.Arc;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant sintético (ADR-0013): ejecuta un trabajo programado <b>en nombre de</b> una organización y una persona, sin
 * saltarse RLS. Activa un contexto de petición cuyo "token" lleva {@code org_id}, {@code org_type}, {@code role} y
 * {@code sub} de esa persona; a partir de ahí {@link TenantContext} y {@link TenantSessionBinder} se comportan igual
 * que en una petición real: cada transacción fija {@code app.org_id}/{@code app.profile_id} y baja a {@code logyx_app}.
 * Los casos de uso ({@code PublishRequestUseCase}) no saben que no hay un usuario humano.
 *
 * <p>No es una puerta al datasource privilegiado: para eso está {@link SystemTx}. Esto es lo opuesto: un job que
 * <i>solo</i> puede hacer lo que esa organización puede hacer. El rol sintético es siempre {@code OPS} (opera, no
 * gestiona). Quien lo use decide la organización y la persona a partir de datos ya existentes (la plantilla y su autor);
 * nunca de entrada de un cliente.
 */
@ApplicationScoped
public class SyntheticTenant {

    @Inject CurrentIdentityAssociation identity;

    public <T> T runAs(UUID orgId, OrgType orgType, UUID profileId, Supplier<T> work) {
        var rc = Arc.container().requestContext();
        boolean activated = !rc.isActive();
        if (activated) rc.activate();
        try {
            var jwt = new SyntheticToken(orgId, orgType, profileId);
            identity.setIdentity(QuarkusSecurityIdentity.builder().setPrincipal(jwt)
                    .addRoles(jwt.getGroups()).build());
            return work.get();
        } finally {
            if (activated) rc.terminate();
        }
    }

    public void runAs(UUID orgId, OrgType orgType, UUID profileId, Runnable work) {
        runAs(orgId, orgType, profileId, () -> { work.run(); return null; });
    }

    /** Un {@link JsonWebToken} en memoria, sin firma: nunca sale de la JVM ni se acepta como credencial. */
    private record SyntheticToken(UUID orgId, OrgType orgType, UUID profileId) implements JsonWebToken {
        @Override public String getName() { return profileId.toString(); }

        @Override public Set<String> getClaimNames() {
            return Set.of("sub", "groups", TenantContext.CLAIM_ORG, TenantContext.CLAIM_ORG_TYPE,
                    TenantContext.CLAIM_ROLE, "raw_token");
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T getClaim(String claimName) {
            Object v = Map.of(
                    "sub", profileId.toString(),
                    "groups", Set.of(orgType.name()),
                    TenantContext.CLAIM_ORG, orgId.toString(),
                    TenantContext.CLAIM_ORG_TYPE, orgType.name(),
                    TenantContext.CLAIM_ROLE, "OPS",
                    "raw_token", "synthetic").get(claimName);
            return (T) v;
        }
    }
}
