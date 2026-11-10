package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.tenancy.OrgType;

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
}
