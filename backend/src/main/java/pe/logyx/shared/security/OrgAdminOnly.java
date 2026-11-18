package pe.logyx.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El endpoint solo lo usa el ADMIN de la organización (F03): gestión de usuarios, perfil de la organización y cuentas
 * de conductores. Lo hace cumplir {@code identity.infrastructure.security.AccountAccessFilter} (403
 * {@code LOGYX-AUTH-008}) antes del método; el caso de uso lo vuelve a comprobar ({@link AccessDenied#requireOrgAdmin}).
 * Se combina con {@code @RolesAllowed} (tipo de organización), no lo reemplaza.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface OrgAdminOnly {}
