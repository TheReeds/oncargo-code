package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.PasswordHasher;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.OpaqueTokens;
import pe.logyx.identity.domain.OrgAccessPolicy;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AccessDenied;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Usuarios de la organización (F03), solo para su ADMIN: listar, invitar, reenviar invitación, cambiar rol,
 * desactivar y reactivar. La organización sale del JWT. Bajo RLS ({@code profiles_own_org}): nada cruza
 * organizaciones. Los conductores se gestionan en la flota ({@code FleetService}).
 *
 * <p>Regla: siempre queda al menos un ADMIN activo. Los cambios de rol/estado bloquean la fila de la organización
 * ({@code SELECT … FOR UPDATE}) antes de contar, así dos ADMIN no se degradan mutuamente a la vez.
 */
@ApplicationScoped
public class OrganizationUserService {

    @Inject ProfileRepository profiles;
    @Inject OrganizationRepository organizations;
    @Inject PasswordHasher passwords;
    @Inject PasswordTokenService passwordTokens;
    @Inject AccountSessions sessions;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;

    @Transactional
    public List<Profile> list() {
        AccessDenied.requireOrgAdmin(tenant);
        return profiles.listOrganizationUsers(tenant.orgId());
    }

    /**
     * Invita a un usuario: crea el perfil sin contraseña utilizable y le envía un enlace (token INVITE,
     * {@code logyx.invitation.ttl}) para definirla con {@code POST /auth/reset-password}.
     */
    public Profile invite(String email, String fullName, Profile.Role role) {
        AccessDenied.requireOrgAdmin(tenant);
        requireOrgRole(role);
        UUID orgId = tenant.orgId();
        UUID adminId = tenant.profileId();
        record Invited(Profile profile, String orgName, String adminName, String link) {}
        // SystemTx: la unicidad del email es global (con RLS solo se ven los usuarios propios) y el token va en
        // password_tokens (solo plataforma), con FK al perfil recién creado: ambos en la misma transacción.
        // La organización es la del JWT; el ADMIN ya se comprobó arriba.
        Invited inv = system.call(() -> {
            if (profiles.emailTaken(email)) {
                throw BusinessException.conflict("LOGYX-AUTH-002", "El email ya está registrado", "email");
            }
            Organization org = organizations.findById(orgId).orElseThrow();
            Profile p = Profile.invite(orgId, email, passwords.hash(OpaqueTokens.generate()), fullName, role, adminId,
                    OffsetDateTime.now());
            profiles.insert(p);
            String link = passwordTokens.issueInvitation(p);
            audit.record(adminId, orgId, "USER_INVITED", "profile", p.id(), "rol=" + role);
            String adminName = profiles.findById(adminId).map(Profile::fullName).orElse(null);
            return new Invited(p, org.legalName(), adminName, link);
        });
        passwordTokens.mailInvitation(inv.profile(), inv.orgName(), inv.adminName(), inv.link());
        return inv.profile();
    }

    /** Reenvía la invitación (nuevo enlace; el anterior deja de valer). Solo si aún no la aceptó. */
    public Profile resendInvitation(UUID id) {
        AccessDenied.requireOrgAdmin(tenant);
        Profile p = inUserTx(() -> {
            Profile x = load(id);
            if (!x.invitationPending()) {
                throw BusinessException.conflict("LOGYX-USR-003", "Este usuario ya aceptó la invitación: si olvidó su "
                        + "contraseña, que use «¿Olvidaste tu contraseña?»");
            }
            if (!x.active()) {
                throw BusinessException.conflict("LOGYX-USR-006", "El usuario está desactivado: reactívalo primero");
            }
            audit.record(tenant.profileId(), tenant.orgId(), "USER_INVITATION_RESENT", "profile", x.id(), null);
            return x;
        });
        UUID orgId = tenant.orgId();
        UUID adminId = tenant.profileId();
        // SystemTx: password_tokens solo es accesible para la plataforma; el perfil ya se validó bajo RLS.
        String[] names = system.call(() -> new String[]{
                organizations.findById(orgId).map(Organization::legalName).orElse(null),
                profiles.findById(adminId).map(Profile::fullName).orElse(null)});
        String link = passwordTokens.issueInvitation(p);
        passwordTokens.mailInvitation(p, names[0], names[1], link);
        return p;
    }

    @Transactional
    public Profile changeRole(UUID id, Profile.Role role) {
        AccessDenied.requireOrgAdmin(tenant);
        requireOrgRole(role);
        organizations.lockForUpdate(tenant.orgId());
        Profile p = load(id);
        // Cambiar el propio rol se permite (p. ej. pasar a OPS tras nombrar a otro ADMIN), salvo que sea el último.
        if (p.role() == role) return p;
        if (role != Profile.Role.ADMIN && OrgAccessPolicy.leavesNoAdmin(p, profiles.countActiveAdmins(tenant.orgId()))) {
            throw lastAdmin();
        }
        Profile.Role before = p.role();
        p.changeRole(role);
        profiles.update(p);
        audit.record(tenant.profileId(), tenant.orgId(), "USER_ROLE_CHANGED", "profile", p.id(), before + "→" + role);
        sessions.revoked(p.id());
        return p;
    }

    @Transactional
    public Profile deactivate(UUID id) {
        AccessDenied.requireOrgAdmin(tenant);
        organizations.lockForUpdate(tenant.orgId());
        Profile p = load(id);
        notSelf(p);
        if (!p.active()) return p;
        if (OrgAccessPolicy.leavesNoAdmin(p, profiles.countActiveAdmins(tenant.orgId()))) throw lastAdmin();
        p.deactivate();
        profiles.update(p);
        audit.record(tenant.profileId(), tenant.orgId(), "USER_DEACTIVATED", "profile", p.id(), null);
        sessions.revoked(p.id());
        return p;
    }

    @Transactional
    public Profile reactivate(UUID id) {
        AccessDenied.requireOrgAdmin(tenant);
        Profile p = load(id);
        if (p.active()) return p;
        p.reactivate();
        profiles.update(p);
        audit.record(tenant.profileId(), tenant.orgId(), "USER_REACTIVATED", "profile", p.id(), null);
        sessions.revoked(p.id());
        return p;
    }

    // ------------------------------------------------------------ helpers

    /** Usuario de la organización actual (404 si es de otra: RLS y filtro explícito), que no sea conductor. */
    private Profile load(UUID id) {
        Profile p = profiles.findById(id).filter(x -> x.organizationId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        if (p.isDriver()) {
            throw BusinessException.conflict("LOGYX-USR-005",
                    "Los conductores se gestionan desde Flota → Conductores");
        }
        return p;
    }

    private void notSelf(Profile p) {
        if (p.id().equals(tenant.profileId())) {
            throw BusinessException.conflict("LOGYX-USR-002",
                    "No puedes desactivar tu propia cuenta: pídeselo a otro administrador");
        }
    }

    private static void requireOrgRole(Profile.Role role) {
        if (role == null || role == Profile.Role.DRIVER) {
            throw BusinessException.badRequest("LOGYX-USR-004",
                    "Elige un rol de la organización: Administrador, Operaciones o Lector", "role");
        }
    }

    private static BusinessException lastAdmin() {
        return BusinessException.conflict("LOGYX-USR-001", "Tu organización debe tener al menos un administrador "
                + "activo: asigna el rol Administrador a otra persona antes de hacer este cambio");
    }

    /** Transacción de usuario (RLS) para una parte del caso de uso; el resto va por SystemTx. */
    private static <T> T inUserTx(java.util.function.Supplier<T> work) {
        return io.quarkus.narayana.jta.QuarkusTransaction.joiningExisting().call(work::get);
    }
}
