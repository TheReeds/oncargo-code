package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import pe.logyx.identity.application.port.out.RefreshTokenRepository;
import pe.logyx.identity.domain.model.RefreshToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheRefreshTokenRepository implements RefreshTokenRepository {

    @Override
    public void insert(RefreshToken token) {
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.apply(token);
        e.persist();
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return RefreshTokenEntity.<RefreshTokenEntity>find("tokenHash", tokenHash)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional().map(RefreshTokenEntity::toDomain);
    }

    @Override
    public void update(RefreshToken token) {
        RefreshTokenEntity e = RefreshTokenEntity.findById(token.id());
        if (e == null) throw new IllegalStateException("Refresh token inexistente");
        e.apply(token);
    }

    @Override
    public int revokeFamily(UUID familyId, OffsetDateTime now) {
        return RefreshTokenEntity.update("revokedAt = ?1 where familyId = ?2 and revokedAt is null", now, familyId);
    }

    @Override
    public int revokeAllOf(UUID profileId, OffsetDateTime now) {
        return RefreshTokenEntity.update("revokedAt = ?1 where profileId = ?2 and revokedAt is null", now, profileId);
    }

    @Override
    public int deleteExpiredBefore(OffsetDateTime cutoff) {
        return (int) RefreshTokenEntity.delete("familyExpiresAt < ?1", cutoff);
    }
}
