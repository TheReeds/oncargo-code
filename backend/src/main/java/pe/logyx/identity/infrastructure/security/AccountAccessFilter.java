package pe.logyx.identity.infrastructure.security;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import pe.logyx.identity.application.SessionValidator;
import pe.logyx.identity.domain.OrgAccessPolicy;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AccessDenied;
import pe.logyx.shared.security.OrgAdminOnly;
import pe.logyx.shared.tenancy.TenantContext;

import java.lang.reflect.Method;

/**
 * Barrera por petición autenticada (ADR-0009), antes del recurso:
 * <ol>
 *   <li><b>Sesión vigente</b>: el claim {@code sv} del JWT debe coincidir con {@code profiles.session_version} y el
 *       usuario debe estar activo; si no, 401 {@code LOGYX-AUTH-401} (igual que un token vencido). Se salta en los
 *       endpoints públicos (login, refresh, olvidé mi contraseña…), que no dependen del token.</li>
 *   <li><b>VIEWER = solo lectura</b> por denegación por defecto ({@link OrgAccessPolicy#viewerMayWrite}): 403
 *       {@code LOGYX-AUTH-007}.</li>
 *   <li><b>{@link OrgAdminOnly}</b> en el método o la clase: solo el ADMIN de la organización, 403
 *       {@code LOGYX-AUTH-008}.</li>
 * </ol>
 * Los endpoints son bloqueantes, así que el filtro corre en un hilo de trabajo (puede leer la BD si la caché vence).
 */
public class AccountAccessFilter {

    @Inject TenantContext tenant;
    @Inject SessionValidator sessions;

    @ServerRequestFilter
    public void filter(ContainerRequestContext ctx, ResourceInfo resource) {
        if (!tenant.isAuthenticated()) return;
        Method method = resource.getResourceMethod();
        Class<?> type = resource.getResourceClass();
        if (method == null || type == null) return;
        if (!requiresAuthentication(method, type)) return;
        if (!sessions.isCurrent(tenant.profileId(), tenant.sessionVersion())) {
            throw new BusinessException(401, "LOGYX-AUTH-401",
                    "Tu sesión se cerró (cambio de contraseña, de rol o cierre de sesión). Inicia sesión de nuevo.");
        }
        String role = tenant.role();
        if ("VIEWER".equals(role) && !OrgAccessPolicy.viewerMayWrite(ctx.getMethod(), ctx.getUriInfo().getPath())) {
            throw AccessDenied.viewerReadOnly();
        }
        if (!"ADMIN".equals(role) && (method.isAnnotationPresent(OrgAdminOnly.class)
                || type.isAnnotationPresent(OrgAdminOnly.class))) {
            throw AccessDenied.adminOnly();
        }
    }

    private static boolean requiresAuthentication(Method m, Class<?> type) {
        return m.isAnnotationPresent(RolesAllowed.class) || m.isAnnotationPresent(Authenticated.class)
                || type.isAnnotationPresent(RolesAllowed.class) || type.isAnnotationPresent(Authenticated.class);
    }
}
