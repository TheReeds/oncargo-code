package pe.logyx.shared.tenancy;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import pe.logyx.shared.domain.BusinessException;

import java.util.Optional;
import java.util.UUID;

/**
 * Quién hace la petición actual: usuario, organización y tipo, tomados del JWT.
 * Es la única fuente de "organización actual" del backend: los servicios nunca
 * reciben el org_id desde el cliente.
 */
@RequestScoped
public class TenantContext {

    public static final String CLAIM_ORG = "org_id";
    public static final String CLAIM_ORG_TYPE = "org_type";
    public static final String CLAIM_ROLE = "role";

    @Inject
    JsonWebToken jwt;

    private boolean system;

    public boolean isAuthenticated() {
        return jwt != null && jwt.getRawToken() != null;
    }

    public Optional<UUID> currentOrgId() {
        if (!isAuthenticated()) return Optional.empty();
        String org = jwt.getClaim(CLAIM_ORG);
        return Optional.ofNullable(org).map(UUID::fromString);
    }

    public UUID orgId() {
        return currentOrgId().orElseThrow(() -> new BusinessException(401, "LOGYX-AUTH-401", "No autenticado"));
    }

    public OrgType orgType() {
        String t = jwt.getClaim(CLAIM_ORG_TYPE);
        return OrgType.valueOf(t);
    }

    public UUID profileId() {
        if (!isAuthenticated()) throw new BusinessException(401, "LOGYX-AUTH-401", "No autenticado");
        return UUID.fromString(jwt.getSubject());
    }

    public boolean isDriver() {
        return isAuthenticated() && jwt.getGroups().contains(Roles.DRIVER);
    }

    /** true mientras se ejecuta una operación de sistema (ver {@link SystemTx}). */
    public boolean isSystem() { return system; }

    void setSystem(boolean system) { this.system = system; }
}
