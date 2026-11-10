package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.domain.BusinessException;

import java.util.Collection;
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

    public Map<UUID, Organization> byIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return organizations.findAllById(ids).stream()
                .collect(Collectors.toMap(Organization::id, Function.identity()));
    }
}
