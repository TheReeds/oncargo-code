package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.event.AccessRequested;
import pe.logyx.identity.application.port.out.AccessRequestLog;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.OrgAccessPolicy;
import pe.logyx.identity.domain.OrgPermission;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * «Pedir acceso» (roadmap v2, 1.7/1.15): quien no tiene un permiso (típicamente un Lector) se lo pide a los ADMIN de
 * su organización. No hay tabla: queda en la auditoría ({@code ACCESS_REQUESTED}) y cada ADMIN recibe un aviso
 * personal por el outbox ({@link AccessRequested}). El ADMIN decide cambiando el rol en «Usuarios y roles».
 * Cualquier rol puede pedirlo (también VIEWER: está en {@link OrgAccessPolicy#viewerMayWrite}).
 */
@ApplicationScoped
public class AccessRequestService {

    @Inject TenantContext tenant;
    @Inject ProfileRepository profiles;
    @Inject AccessRequestLog log;
    @Inject AuditService audit;
    @Inject Event<AccessRequested> requested;

    @ConfigProperty(name = "logyx.access-request.cooldown", defaultValue = "PT10M")
    Duration cooldown;

    public record Result(OrgPermission permission, int notifiedAdmins) {}

    @Transactional
    public Result request(OrgPermission permission, String reason) {
        UUID me = tenant.profileId();
        UUID org = tenant.orgId();
        Profile requester = profiles.findById(me).orElseThrow(() -> BusinessException.notFound("Usuario"));
        if (OrgAccessPolicy.permissions(requester.role(), tenant.orgType()).contains(permission)) {
            throw BusinessException.conflict("LOGYX-ACC-001", "Tu rol ya tiene este permiso. Si no ves la acción, "
                    + "vuelve a iniciar sesión.", "permission");
        }
        if (log.countSince(me, OffsetDateTime.now().minus(cooldown)) > 0) {
            throw new BusinessException(429, "LOGYX-ACC-002", "Ya enviaste un pedido de acceso hace poco. Los "
                    + "administradores de tu empresa ya tienen el aviso.");
        }
        // Bajo RLS: los perfiles de la propia organización (profiles_own_org).
        List<UUID> admins = profiles.listOrganizationUsers(org).stream()
                .filter(p -> p.isOrgAdmin() && p.canLogIn() && !p.id().equals(me)).map(Profile::id).toList();
        if (admins.isEmpty()) {
            throw BusinessException.conflict("LOGYX-ACC-003", "Tu empresa no tiene otro administrador activo a quien "
                    + "pedirle acceso. Escríbenos y te ayudamos.");
        }
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        audit.record(me, org, "ACCESS_REQUESTED", "profile", me,
                "permiso=" + permission.name() + (why == null ? "" : "; motivo=" + why));
        requested.fire(new AccessRequested(org, me, requester.fullName(), OrgAccessPolicy.label(requester.role()),
                permission.name(), why, admins));
        return new Result(permission, admins.size());
    }
}
