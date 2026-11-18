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
    /** Versión de sesión del perfil (V14): si no coincide con la BD, el token ya no vale. Ausente = 0. */
    public static final String CLAIM_SESSION_VERSION = "sv";

    @Inject
    JsonWebToken jwt;

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

    /** Rol de organización del JWT ({@code ADMIN|OPS|VIEWER|DRIVER}) o null sin usuario. */
    public String role() {
        if (!isAuthenticated()) return null;
        Object r = jwt.getClaim(CLAIM_ROLE);
        return r == null ? null : r.toString();
    }

    public boolean isOrgAdmin() {
        return "ADMIN".equals(role());
    }

    /** Versión de sesión que trae el token ({@code sv}; los tokens anteriores a V14 no la traen: 0). */
    public int sessionVersion() {
        if (!isAuthenticated()) return 0;
        Object v = jwt.getClaim(CLAIM_SESSION_VERSION);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof jakarta.json.JsonNumber n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return -1; }
        }
        return 0;
    }

    public boolean isDriver() {
        return isAuthenticated() && jwt.getGroups().contains(Roles.DRIVER);
    }

    /** true mientras se ejecuta una operación de sistema (ver {@link SystemTx}). */
    public boolean isSystem() { return SystemTx.isActive(); }
}
