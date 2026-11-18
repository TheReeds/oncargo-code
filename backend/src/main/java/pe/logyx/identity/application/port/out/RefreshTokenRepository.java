package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.RefreshToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Refresh tokens. Solo desde {@code SystemTx}: logyx_app no tiene privilegios sobre la tabla (V14). */
public interface RefreshTokenRepository {
    void insert(RefreshToken token);
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);
    void update(RefreshToken token);
    int revokeFamily(UUID familyId, OffsetDateTime now);
    int revokeAllOf(UUID profileId, OffsetDateTime now);
    int deleteExpiredBefore(OffsetDateTime cutoff);
}
