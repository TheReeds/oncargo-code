package pe.logyx.identity.application;

import pe.logyx.shared.tenancy.SystemTx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.PasswordHasher;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.application.port.out.TokenIssuer;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;

/** Casos de uso de acceso: registro, login y usuario actual. */
@ApplicationScoped
public class AuthService {

    @Inject SystemTx systemTx;

    @Inject OrganizationRepository organizations;
    @Inject ProfileRepository profiles;
    @Inject PasswordHasher passwords;
    @Inject TokenIssuer tokens;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject RefreshSessions refreshSessions;
    @Inject AccountSessions sessions;
    @Inject LegalTerms legalTerms;
    @Inject TermsService termsService;
    @Inject pe.logyx.identity.application.port.out.TermsAcceptanceRepository termsAcceptances;

    /** Registra organización + usuario administrador. No hay usuario aún: corre sin RLS. */
    // SystemTx: sin usuario aún; verifica RUC/email en todas las organizaciones.
    public AuthResult register(RegisterCommand cmd) {
        return systemTx.call(() -> {
            Organization org = Organization.register(cmd.organizationType(), cmd.legalName(), cmd.ruc(),
                    cmd.city(), cmd.phone());
            if (organizations.existsByRuc(org.ruc())) {
                throw BusinessException.conflict("LOGYX-ORG-002", "Ya existe una organización con ese RUC", "ruc");
            }
            if (profiles.emailTaken(cmd.email())) {
                throw BusinessException.conflict("LOGYX-AUTH-002", "El email ya está registrado", "email");
            }
            organizations.insert(org);

            Profile admin = Profile.create(org.id(), cmd.email(), passwords.hash(cmd.password()), cmd.fullName(),
                    Profile.Role.ADMIN, OffsetDateTime.now());
            profiles.insert(admin);

            OffsetDateTime now = OffsetDateTime.now();
            String terms = cmd.termsVersion() == null ? null : legalTerms.ensureCurrent(cmd.termsVersion());
            if (terms != null) termsAcceptances.insert(admin.id(), org.id(), terms, now);
            audit.record(admin.id(), org.id(), "REGISTER", "organization", org.id(),
                    terms == null ? "consentimiento Ley 29733 aceptado"
                            : "consentimiento Ley 29733 y términos " + terms + " aceptados");
            return issue(termsService.account(admin, org));
        });
    }

    /**
     * Alta de un operador LOGYX (organización OPERATOR + usuario ADMIN). No se expone por HTTP: las cuentas de
     * operador las crea la plataforma (seed dev, tests o un proceso interno). Corre sin usuario autenticado.
     */
    // SystemTx: alta interna de plataforma (seed dev, tests), sin usuario.
    public AuthResult provisionOperator(String legalName, String ruc, String fullName, String email, String password) {
        return systemTx.call(() -> {
            Organization org = Organization.provisionOperator(legalName, ruc, "Lima");
            if (organizations.existsByRuc(org.ruc())) {
                throw BusinessException.conflict("LOGYX-ORG-002", "Ya existe una organización con ese RUC", "ruc");
            }
            if (profiles.emailTaken(email)) {
                throw BusinessException.conflict("LOGYX-AUTH-002", "El email ya está registrado", "email");
            }
            organizations.insert(org);
            Profile admin = Profile.create(org.id(), email, passwords.hash(password), fullName, Profile.Role.ADMIN,
                    OffsetDateTime.now());
            profiles.insert(admin);
            audit.record(admin.id(), org.id(), "OPERATOR_PROVISIONED", "organization", org.id(), null);
            return issue(termsService.account(admin, org));
        });
    }

    /**
     * Alta interna de un usuario con contraseña en una organización existente (seed dev y tests; no se expone por
     * HTTP: por la API los usuarios se invitan). Idempotente por email: si ya existe, no hace nada.
     */
    // SystemTx: alta interna de plataforma (seed dev, tests), sin usuario; unicidad global del email.
    public void provisionUser(java.util.UUID organizationId, String email, String password, String fullName,
                              Profile.Role role) {
        systemTx.run(() -> {
            if (profiles.emailTaken(email)) return;
            Profile p = Profile.create(organizationId, email, passwords.hash(password), fullName, role,
                    OffsetDateTime.now());
            profiles.insert(p);
            audit.record(null, organizationId, "USER_PROVISIONED", "profile", p.id(), "rol=" + role);
        });
    }

    /** Sin usuario autenticado aún: corre sin RLS (necesita buscar el email en todas las organizaciones). */
    public AuthResult login(String email, String password) {
        return login(email, password, false, null);
    }

    /**
     * Login. Con {@code refresh=true} (opt-in del móvil/conductor, F24) el access token dura
     * {@code logyx.jwt.refresh-access-ttl} y la respuesta trae un refresh token rotativo; sin él, la sesión web de
     * {@code logyx.jwt.ttl} (revocable por {@code sv}). Registra {@code last_login_at}.
     */
    // SystemTx: busca el email en todas las organizaciones antes de que exista un JWT.
    public AuthResult login(String email, String password, boolean refresh, String userAgent) {
        return systemTx.call(() -> {
            Profile p = profiles.findByEmail(email)
                    .filter(Profile::canLogIn)
                    .filter(x -> passwords.matches(password, x.passwordHash()))
                    .orElseThrow(() -> new BusinessException(401, "LOGYX-AUTH-001", "Credenciales inválidas"));
            Organization org = organizations.findById(p.organizationId()).orElseThrow();
            p.recordLogin(OffsetDateTime.now());
            profiles.update(p);
            return refresh ? refreshSessions.start(p, org, userAgent) : issue(termsService.account(p, org));
        });
    }

    /**
     * Cambio de contraseña con sesión (cualquier rol, también DRIVER y VIEWER). Verifica la actual
     * ({@code LOGYX-AUTH-006}), sube la versión de sesión (cierra las demás sesiones) y devuelve una sesión nueva
     * para que la actual siga (la web reemplaza su cookie).
     */
    @Transactional
    public AuthResult changePassword(String currentPassword, String newPassword, boolean refresh, String userAgent) {
        Profile p = profiles.findById(tenant.profileId()).orElseThrow(() -> BusinessException.notFound("Usuario"));
        if (!passwords.matches(currentPassword, p.passwordHash())) {
            throw BusinessException.badRequest("LOGYX-AUTH-006", "La contraseña actual no es correcta",
                    "currentPassword");
        }
        if (passwords.matches(newPassword, p.passwordHash())) {
            throw BusinessException.badRequest("LOGYX-AUTH-010", "La nueva contraseña debe ser distinta de la actual",
                    "newPassword");
        }
        p.changePassword(passwords.hash(newPassword), OffsetDateTime.now());
        profiles.update(p);
        audit.record(p.id(), p.organizationId(), "PASSWORD_CHANGED", "profile", p.id(), null);
        sessions.revoked(p.id());
        Organization org = organizations.findById(p.organizationId()).orElseThrow();
        return refresh ? refreshSessions.start(p, org, userAgent) : issue(termsService.account(p, org));
    }

    /** "Cerrar sesión en todos los dispositivos" (F55): sube la versión de sesión; todos los tokens dejan de valer. */
    @Transactional
    public void logoutAll() {
        Profile p = profiles.findById(tenant.profileId()).orElseThrow(() -> BusinessException.notFound("Usuario"));
        p.revokeSessions();
        profiles.update(p);
        audit.record(p.id(), p.organizationId(), "LOGOUT_ALL", "profile", p.id(), null);
        sessions.revoked(p.id());
    }

    @Transactional
    public UserAccount me() {
        Profile p = profiles.findById(tenant.profileId())
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        Organization org = organizations.findById(p.organizationId()).orElseThrow();
        return termsService.account(p, org);
    }

    private AuthResult issue(UserAccount account) {
        return new AuthResult(tokens.issue(account.profile(), account.organization()), tokens.ttlSeconds(), account);
    }
}
