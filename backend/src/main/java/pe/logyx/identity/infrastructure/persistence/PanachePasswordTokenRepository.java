package pe.logyx.identity.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import pe.logyx.identity.application.port.out.PasswordTokenRepository;
import pe.logyx.identity.domain.model.PasswordToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanachePasswordTokenRepository implements PasswordTokenRepository {

    @Override
    public void insert(PasswordToken token) {
        PasswordTokenEntity e = new PasswordTokenEntity();
        e.apply(token);
        e.persist();
    }

    @Override
    public Optional<PasswordToken> findByHashForUpdate(String tokenHash) {
        return PasswordTokenEntity.<PasswordTokenEntity>find("tokenHash", tokenHash)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional().map(PasswordTokenEntity::toDomain);
    }

    @Override
    public Optional<PasswordToken> findByHash(String tokenHash) {
        return PasswordTokenEntity.<PasswordTokenEntity>find("tokenHash", tokenHash).firstResultOptional()
                .map(PasswordTokenEntity::toDomain);
    }

    @Override
    public void update(PasswordToken token) {
        PasswordTokenEntity e = PasswordTokenEntity.findById(token.id());
        if (e == null) throw new IllegalStateException("Token inexistente");
        e.apply(token);
    }

    @Override
    public int revokeUnused(UUID profileId, OffsetDateTime now) {
        return PasswordTokenEntity.update("revokedAt = ?1 where profileId = ?2 and usedAt is null and revokedAt is null",
                now, profileId);
    }

    @Override
    public int deleteExpiredBefore(OffsetDateTime cutoff) {
        return (int) PasswordTokenEntity.delete("expiresAt < ?1", cutoff);
    }
}
