package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.Profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {
    Optional<Profile> findById(UUID id);
    Optional<Profile> findByEmail(String email);
    List<Profile> findAllById(Collection<UUID> ids);
    boolean emailTaken(String email);
    void insert(Profile profile);
    /** Actualiza la fila existente (contraseña, rol, estado, versión de sesión, último acceso). */
    void update(Profile profile);
    /** Usuarios de la organización que no son conductores (los conductores se gestionan en la flota). */
    List<Profile> listOrganizationUsers(UUID organizationId);
    /** ADMIN activos con contraseña propia (las invitaciones pendientes no cuentan). */
    long countActiveAdmins(UUID organizationId);
}
