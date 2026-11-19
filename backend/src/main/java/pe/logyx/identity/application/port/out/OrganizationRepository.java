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
    /** V23 (2.15): por RUC (registro → «Pedir acceso a la organización»). */
    default Optional<Organization> findByRuc(String ruc) { return Optional.empty(); }
    List<UUID> findIdsByType(OrgType type);
    long countByType(OrgType type);
    void insert(Organization organization);
    /**
     * Bloquea la fila de la organización hasta el fin de la transacción ({@code SELECT … FOR UPDATE}): serializa
     * los cambios de rol/estado de sus usuarios (regla "al menos un ADMIN activo") y la edición del perfil.
     */
    void lockForUpdate(UUID id);
    /** Actualiza la fila existente (verificación). */
    void update(Organization organization);
    /** Bandeja del operador: filtra por estado de verificación y tipo (null = todos), las más antiguas primero. */
    pe.logyx.shared.domain.Paged<Organization> search(Organization.VerificationStatus status, OrgType type,
                                                      pe.logyx.shared.domain.PageRequest page);
}
