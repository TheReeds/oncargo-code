package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.tenancy.OrgType;

import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheOrganizationRepository implements OrganizationRepository {

    @Override
    public Optional<Organization> findById(UUID id) {
        return OrganizationEntity.<OrganizationEntity>findByIdOptional(id).map(OrganizationEntity::toDomain);
    }

    @Override
    public List<Organization> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return OrganizationEntity.<OrganizationEntity>list("id in ?1", ids).stream()
                .map(OrganizationEntity::toDomain).toList();
    }

    @Override
    public boolean existsByRuc(String ruc) {
        return OrganizationEntity.count("ruc", ruc) > 0;
    }

    @Override
    public List<UUID> findIdsByType(OrgType type) {
        return OrganizationEntity.<OrganizationEntity>list("type", type).stream().map(e -> e.id).toList();
    }

    @Override
    public long countByType(OrgType type) {
        return OrganizationEntity.count("type", type);
    }

    @Override
    public void insert(Organization organization) {
        OrganizationEntity.from(organization).persist();
    }

    @Override
    public void lockForUpdate(UUID id) {
        // Con RLS, FOR UPDATE exige la política de UPDATE (orgs_update_own): solo la propia organización.
        var rows = OrganizationEntity.getEntityManager()
                .createNativeQuery("SELECT id FROM organizations WHERE id = ?1 FOR UPDATE")
                .setParameter(1, id).getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("Organización no visible para bloquear: " + id);
    }

    @Override
    public void update(Organization organization) {
        OrganizationEntity e = OrganizationEntity.findById(organization.id());
        if (e == null) throw new IllegalStateException("Organización inexistente: " + organization.id());
        e.apply(organization);
    }

    @Override
    public Paged<Organization> search(Organization.VerificationStatus status, OrgType type, PageRequest page) {
        StringBuilder where = new StringBuilder("type <> ?1");
        List<Object> params = new ArrayList<>(List.of(OrgType.OPERATOR));
        if (status != null) { params.add(status); where.append(" and verificationStatus = ?").append(params.size()); }
        if (type != null) { params.add(type); where.append(" and type = ?").append(params.size()); }
        var q = OrganizationEntity.<OrganizationEntity>find(where + " order by createdAt, id", params.toArray())
                .page(page.page(), page.size());
        long total = OrganizationEntity.count(where.toString(), params.toArray());
        return new Paged<>(q.list().stream().map(OrganizationEntity::toDomain).toList(), page, total);
    }
}
