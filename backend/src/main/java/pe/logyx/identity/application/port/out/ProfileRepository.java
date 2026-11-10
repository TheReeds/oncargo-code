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
}
