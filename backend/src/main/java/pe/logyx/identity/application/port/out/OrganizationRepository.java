package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.tenancy.OrgType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    Optional<Organization> findById(UUID id);
    List<Organization> findAllById(Collection<UUID> ids);
    boolean existsByRuc(String ruc);
    List<UUID> findIdsByType(OrgType type);
    long countByType(OrgType type);
    void insert(Organization organization);
    /** Actualiza la fila existente (verificación). */
    void update(Organization organization);
    /** Bandeja del operador: filtra por estado de verificación y tipo (null = todos), las más antiguas primero. */
    pe.logyx.shared.domain.Paged<Organization> search(Organization.VerificationStatus status, OrgType type,
                                                      pe.logyx.shared.domain.PageRequest page);
}
