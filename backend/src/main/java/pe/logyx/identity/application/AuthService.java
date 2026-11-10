package pe.logyx.identity.application;

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

    @Inject OrganizationRepository organizations;
    @Inject ProfileRepository profiles;
    @Inject PasswordHasher passwords;
    @Inject TokenIssuer tokens;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    /** Registra organización + usuario administrador. No hay usuario aún: corre sin RLS. */
    @Transactional
    public AuthResult register(RegisterCommand cmd) {
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

        audit.record(admin.id(), org.id(), "REGISTER", "organization", org.id(), "consentimiento Ley 29733 aceptado");
        return issue(new UserAccount(admin, org));
    }

    /** Sin usuario autenticado aún: corre sin RLS (necesita buscar el email en todas las organizaciones). */
    @Transactional
    public AuthResult login(String email, String password) {
        Profile p = profiles.findByEmail(email)
                .filter(Profile::active)
                .filter(x -> passwords.matches(password, x.passwordHash()))
                .orElseThrow(() -> new BusinessException(401, "LOGYX-AUTH-001", "Credenciales inválidas"));
        Organization org = organizations.findById(p.organizationId()).orElseThrow();
        return issue(new UserAccount(p, org));
    }

    @Transactional
    public UserAccount me() {
        Profile p = profiles.findById(tenant.profileId())
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        Organization org = organizations.findById(p.organizationId()).orElseThrow();
        return new UserAccount(p, org);
    }

    private AuthResult issue(UserAccount account) {
        return new AuthResult(tokens.issue(account.profile(), account.organization()), tokens.ttlSeconds(), account);
    }
}
