package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.Organization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    Optional<Organization> findById(UUID id);
    List<Organization> findAllById(Collection<UUID> ids);
    boolean existsByRuc(String ruc);
    void insert(Organization organization);
}
