package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.model.Profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheProfileRepository implements ProfileRepository {

    @Override
    public Optional<Profile> findById(UUID id) {
        return ProfileEntity.<ProfileEntity>findByIdOptional(id).map(ProfileEntity::toDomain);
    }

    @Override
    public Optional<Profile> findByEmail(String email) {
        return ProfileEntity.<ProfileEntity>find("lower(email) = ?1", email.toLowerCase())
                .firstResultOptional().map(ProfileEntity::toDomain);
    }

    @Override
    public List<Profile> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return ProfileEntity.<ProfileEntity>list("id in ?1", ids).stream().map(ProfileEntity::toDomain).toList();
    }

    @Override
    public boolean emailTaken(String email) {
        return ProfileEntity.count("lower(email) = ?1", email.toLowerCase()) > 0;
    }

    @Override
    public void insert(Profile profile) {
        ProfileEntity.from(profile).persist();
    }
}
