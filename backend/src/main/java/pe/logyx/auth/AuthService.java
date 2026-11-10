package pe.logyx.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.auth.AuthDtos.*;
import pe.logyx.organization.Organization;
import pe.logyx.organization.Profile;
import pe.logyx.organization.RucValidator;
import pe.logyx.shared.api.BusinessException;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;

@ApplicationScoped
public class AuthService {

    @Inject TokenService tokens;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    /** Registra organización + usuario administrador. No hay usuario aún: corre sin RLS. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.organizationType() == OrgType.OPERATOR) {
            throw BusinessException.forbidden("Las cuentas de operador las crea LOGYX");
        }
        if (!RucValidator.isValid(req.ruc())) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "El RUC no es válido (dígito verificador)");
        }
        if (Organization.existsByRuc(req.ruc())) {
            throw BusinessException.conflict("LOGYX-ORG-002", "Ya existe una organización con ese RUC");
        }
        if (Profile.emailTaken(req.email())) {
            throw BusinessException.conflict("LOGYX-AUTH-002", "El email ya está registrado");
        }

        Organization org = new Organization();
        org.type = req.organizationType();
        org.legalName = req.legalName().trim();
        org.ruc = req.ruc();
        org.rucVerification = Organization.RucVerification.CHECKSUM;
        org.city = req.city();
        org.phone = req.phone();
        org.persist();

        Profile admin = new Profile();
        admin.organization = org;
        admin.email = req.email().trim().toLowerCase();
        admin.passwordHash = BcryptUtil.bcryptHash(req.password());
        admin.fullName = req.fullName().trim();
        admin.role = Profile.Role.ADMIN;
        admin.consentAcceptedAt = OffsetDateTime.now();
        admin.persist();

        audit.record(admin.id, org.id, "REGISTER", "organization", org.id, "consentimiento Ley 29733 aceptado");
        return new AuthResponse(tokens.issue(admin), tokens.ttlSeconds(), UserInfo.of(admin));
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Profile p = Profile.findByEmail(req.email())
                .filter(x -> x.active)
                .filter(x -> BcryptUtil.matches(req.password(), x.passwordHash))
                .orElseThrow(() -> new BusinessException(401, "LOGYX-AUTH-001", "Credenciales inválidas"));
        return new AuthResponse(tokens.issue(p), tokens.ttlSeconds(), UserInfo.of(p));
    }

    @Transactional
    public UserInfo me() {
        Profile p = Profile.<Profile>findByIdOptional(tenant.profileId())
                .orElseThrow(() -> BusinessException.notFound("Usuario"));
        return UserInfo.of(p);
    }
}
