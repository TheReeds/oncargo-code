package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.event.OrganizationVerificationChanged;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AccessDenied;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de la propia organización (F20). Cualquier usuario lo lee; solo su ADMIN lo edita. Teléfono, ciudad y
 * dirección siempre; razón social y RUC solo si la verificación está {@code PENDING} o {@code REJECTED} (con
 * {@code VERIFIED}/{@code SUSPENDED}: 409 {@code LOGYX-ORG-007}). Si estaba {@code REJECTED}, corregirlos la devuelve
 * a {@code PENDING} y avisa a los operadores (outbox, igual que la verificación).
 */
@ApplicationScoped
public class OrganizationProfileService {

    @Inject OrganizationRepository organizations;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<OrganizationVerificationChanged> verificationChanged;

    @Transactional
    public Organization mine() {
        return organizations.findById(tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Organización"));
    }

    public Organization update(UpdateOrganizationCommand cmd) {
        AccessDenied.requireOrgAdmin(tenant);
        UUID orgId = tenant.orgId();
        UUID actor = tenant.profileId();
        // SystemTx: devolver una organización REJECTED a PENDING cambia columnas de verificación que el trigger de V13
        // solo deja tocar al operador o a la plataforma, y debe ser atómico con la corrección de los datos. Solo
        // actúa sobre la organización del JWT y después de comprobar que el usuario es su ADMIN.
        return system.call(() -> {
            organizations.lockForUpdate(orgId);
            Organization o = organizations.findById(orgId).orElseThrow(() -> BusinessException.notFound("Organización"));
            List<String> changed = new ArrayList<>();
            if (cmd.legalName() != null && !cmd.legalName().trim().equals(o.legalName())) changed.add("legalName");
            String newRuc = cmd.ruc() == null ? null : cmd.ruc().trim();
            if (newRuc != null && !newRuc.equals(o.ruc())) {
                changed.add("ruc");
                if (organizations.existsByRuc(newRuc)) {
                    throw BusinessException.conflict("LOGYX-ORG-002", "Ya existe una organización con ese RUC", "ruc");
                }
            }
            boolean resubmitted = o.changeLegalData(cmd.legalName(), newRuc, OffsetDateTime.now());
            if (cmd.city() != null && !cmd.city().trim().equals(nz(o.city()))) changed.add("city");
            if (cmd.phone() != null && !cmd.phone().trim().equals(nz(o.phone()))) changed.add("phone");
            if (cmd.address() != null && !cmd.address().trim().equals(nz(o.address()))) changed.add("address");
            o.updateContact(cmd.city(), cmd.phone(), cmd.address());
            if (changed.isEmpty()) return o;
            organizations.update(o);
            audit.record(actor, orgId, "ORGANIZATION_UPDATED", "organization", orgId, String.join(",", changed));
            if (resubmitted) {
                audit.record(actor, orgId, "ORGANIZATION_VERIFICATION_RESUBMITTED", "organization", orgId, null);
                verificationChanged.fire(new OrganizationVerificationChanged(orgId,
                        Organization.VerificationStatus.PENDING, "La organización corrigió su razón social o RUC"));
            }
            return o;
        });
    }

    private static String nz(String v) { return v == null ? "" : v; }
}
