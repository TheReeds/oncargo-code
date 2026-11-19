package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Acciones del operador sobre las cuentas de otras organizaciones (roadmap 2.15). Bajo la RLS del operador
 * ({@code profiles_operator} lee; {@code profiles_operator_update}, V23, escribe; un trigger le impide tocar algo que no
 * sea el estado). Suspender cierra las sesiones y el ADMIN de la organización no puede reactivarlo (LOGYX-USR-007).
 */
@ApplicationScoped
public class OperatorAccountService {

    @Inject ProfileRepository profiles;
    @Inject OrganizationRepository organizations;
    @Inject AccountSessions sessions;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    /** Personas de la organización, conductores incluidos, en orden de alta. */
    @Transactional
    public List<Profile> usersOf(UUID organizationId) {
        requireOperator();
        return profiles.listByOrganization(organizationId);
    }

    /** Suspende a una persona de una PYME o un transportista con motivo (3–300, RN07 no aplica: lo lee OnCargo). */
    @Transactional
    public Profile suspend(UUID profileId, String reason) {
        Profile p = target(profileId);
        String why = reason(reason);
        if (p.operatorSuspendedAt() != null) return p;
        p.suspendByOperator(why, OffsetDateTime.now());
        profiles.update(p);
        audit.record(tenant.profileId(), tenant.orgId(), "USER_SUSPENDED_BY_OPERATOR", "profile", p.id(),
                "organizacion=" + p.organizationId() + " · motivo: " + why);
        sessions.revoked(p.id());
        return p;
    }

    /** Levanta la suspensión del operador y reactiva a la persona. */
    @Transactional
    public Profile reactivate(UUID profileId, String reason) {
        Profile p = target(profileId);
        if (p.operatorSuspendedAt() == null && p.active()) return p;
        p.reactivateByOperator();
        profiles.update(p);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        audit.record(tenant.profileId(), tenant.orgId(), "USER_REACTIVATED_BY_OPERATOR", "profile", p.id(),
                "organizacion=" + p.organizationId() + (why == null ? "" : " · motivo: " + why));
        sessions.revoked(p.id());
        return p;
    }

    private Profile target(UUID profileId) {
        requireOperator();
        Profile p = profiles.findById(profileId).orElseThrow(() -> BusinessException.notFound("Usuario"));
        Organization o = organizations.findById(p.organizationId())
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        if (o.type() == OrgType.OPERATOR) {
            throw BusinessException.conflict("LOGYX-USR-008", "Las cuentas del operador no se suspenden desde aquí");
        }
        return p;
    }

    private void requireOperator() {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador");
    }

    static String reason(String reason) {
        if (reason == null || reason.strip().length() < 3) {
            throw BusinessException.badRequest("LOGYX-ORG-006", "Indica el motivo (mínimo 3 caracteres)", "reason");
        }
        String r = reason.strip();
        return r.length() > 300 ? r.substring(0, 300) : r;
    }

}
