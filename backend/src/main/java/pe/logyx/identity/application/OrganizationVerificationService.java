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
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bandeja de verificación del operador (RN01, RF08, F05): listar, verificar, rechazar y suspender organizaciones.
 * Escribe con RLS del operador (política {@code orgs_operator_update}, V13; un trigger impide que otra organización
 * toque estas columnas). Avisa a la organización por el outbox.
 */
@ApplicationScoped
public class OrganizationVerificationService {

    @Inject OrganizationRepository organizations;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject Event<OrganizationVerificationChanged> changed;

    @Transactional
    public Paged<Organization> search(Organization.VerificationStatus status, OrgType type, PageRequest page) {
        return organizations.search(status, type, page);
    }

    @Transactional
    public Organization verify(UUID id, String note) {
        Organization o = load(id);
        o.verify(tenant.profileId(), note, OffsetDateTime.now());
        return save(o, "ORGANIZATION_VERIFIED");
    }

    @Transactional
    public Organization reject(UUID id, String reason) {
        Organization o = load(id);
        o.reject(tenant.profileId(), reason, OffsetDateTime.now());
        return save(o, "ORGANIZATION_REJECTED");
    }

    @Transactional
    public Organization suspend(UUID id, String reason) {
        Organization o = load(id);
        o.suspend(tenant.profileId(), reason, OffsetDateTime.now());
        return save(o, "ORGANIZATION_SUSPENDED");
    }

    private Organization load(UUID id) {
        return organizations.findById(id).orElseThrow(() -> BusinessException.notFound("Organización"));
    }

    private Organization save(Organization o, String action) {
        organizations.update(o);
        audit.record(tenant.profileId(), tenant.orgId(), action, "organization", o.id(), o.verification().note());
        changed.fire(new OrganizationVerificationChanged(o.id(), o.verificationStatus(), o.verification().note()));
        return o;
    }
}
