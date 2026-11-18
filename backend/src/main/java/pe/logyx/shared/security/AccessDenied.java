package pe.logyx.shared.security;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

/** Errores 403 por rol de organización (F03, F47: el mensaje dice quién puede hacerlo). */
public final class AccessDenied {

    private AccessDenied() {}

    /** VIEWER intentando escribir. */
    public static BusinessException viewerReadOnly() {
        return new BusinessException(403, "LOGYX-AUTH-007", "Tu rol Lector solo permite consultar. Pide a un "
                + "administrador de tu organización que haga este cambio o que te asigne el rol Operaciones.");
    }

    /** Acción reservada al ADMIN de la organización. */
    public static BusinessException adminOnly() {
        return new BusinessException(403, "LOGYX-AUTH-008", "Solo un administrador de tu organización puede "
                + "gestionar usuarios, el perfil de la organización y las cuentas de los conductores.");
    }

    /** Segunda barrera en los casos de uso (la primera es {@link OrgAdminOnly} en el recurso). */
    public static void requireOrgAdmin(TenantContext tenant) {
        if (!tenant.isOrgAdmin()) throw adminOnly();
    }
}
