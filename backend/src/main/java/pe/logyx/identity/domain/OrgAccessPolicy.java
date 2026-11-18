package pe.logyx.identity.domain;

import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.tenancy.OrgType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Permisos por rol de organización (F03), en Java puro:
 * <ul>
 *   <li>{@code ADMIN}: todo.</li>
 *   <li>{@code OPS}: opera, sin gestión de usuarios, perfil de la organización ni cuentas de conductores.</li>
 *   <li>{@code VIEWER}: solo lectura. <b>Denegación por defecto</b>: cualquier método no seguro (POST, PUT, PATCH,
 *       DELETE) se rechaza salvo la lista corta de autoservicio de {@link #viewerMayWrite}. Así los endpoints nuevos
 *       quedan protegidos sin anotarlos.</li>
 *   <li>{@code DRIVER}: grupo JWT propio, sus endpoints ya están acotados por {@code @RolesAllowed}.</li>
 * </ul>
 */
public final class OrgAccessPolicy {

    private OrgAccessPolicy() {}

    /** Escrituras permitidas a un VIEWER: su propia cuenta y sesión, leer avisos y el cotizador (sin efectos). */
    private static final List<Pattern> VIEWER_WRITES = List.of(
            Pattern.compile("/api/v1/auth/[a-z-]+(/[a-z-]+)?"),
            Pattern.compile("/api/v1/notifications/[^/]+/read"),
            Pattern.compile("/api/v1/notifications/read-all"),
            Pattern.compile("/api/v1/pricing/calculate"));

    public static boolean isSafeMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }

    /** true si un VIEWER puede ejecutar {@code method path}. */
    public static boolean viewerMayWrite(String method, String path) {
        if (isSafeMethod(method)) return true;
        String p = normalize(path);
        return VIEWER_WRITES.stream().anyMatch(rx -> rx.matcher(p).matches());
    }

    public static Set<OrgPermission> permissions(Profile.Role role, OrgType orgType) {
        return switch (role) {
            case ADMIN -> orgType == OrgType.CARRIER ? EnumSet.allOf(OrgPermission.class)
                    : EnumSet.of(OrgPermission.READ, OrgPermission.OPERATE, OrgPermission.MANAGE_USERS,
                            OrgPermission.MANAGE_ORGANIZATION);
            case OPS -> EnumSet.of(OrgPermission.READ, OrgPermission.OPERATE);
            case VIEWER, DRIVER -> EnumSet.of(OrgPermission.READ);
        };
    }

    /** Nombre del rol para los mensajes al usuario. */
    public static String label(Profile.Role role) {
        return switch (role) {
            case ADMIN -> "Administrador";
            case OPS -> "Operaciones";
            case VIEWER -> "Lector";
            case DRIVER -> "Conductor";
        };
    }

    /**
     * Regla "al menos un ADMIN activo por organización": {@code activeAdmins} es el total actual (con la fila de la
     * organización bloqueada) y {@code target} el usuario que se degrada o desactiva.
     */
    public static boolean leavesNoAdmin(Profile target, long activeAdmins) {
        boolean targetCounts = target.isOrgAdmin() && target.active() && !target.invitationPending();
        return targetCounts && activeAdmins <= 1;
    }

    private static String normalize(String path) {
        String p = path == null ? "" : path;
        if (!p.startsWith("/")) p = "/" + p;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
