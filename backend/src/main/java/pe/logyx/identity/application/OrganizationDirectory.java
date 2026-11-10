package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.domain.BusinessException;

import pe.logyx.shared.tenancy.OrgType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consulta de organizaciones. Es la puerta de entrada de otros módulos a
 * los datos de organización (perfil público: RLS {@code orgs_read}).
 */
@ApplicationScoped
@Transactional
public class OrganizationDirectory {

    @Inject OrganizationRepository organizations;

    public Organization get(UUID id) {
        return organizations.findById(id).orElseThrow(() -> BusinessException.notFound("Organización"));
    }

    /** Ids de las organizaciones de un tipo (p. ej. operadores destinatarios de alertas). orgs_read es público. */
    public List<UUID> idsOfType(OrgType type) {
        return organizations.findIdsByType(type);
    }

    public long countOfType(OrgType type) {
        return organizations.countByType(type);
    }

    public Map<UUID, Organization> byIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return organizations.findAllById(ids).stream()
                .collect(Collectors.toMap(Organization::id, Function.identity()));
    }
}
