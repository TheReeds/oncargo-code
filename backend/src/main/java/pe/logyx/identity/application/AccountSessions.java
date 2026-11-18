package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import pe.logyx.identity.application.event.SessionsRevoked;
import pe.logyx.identity.application.port.out.RefreshTokenRepository;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Efectos de subir la versión de sesión de un usuario: vacía la caché de {@link SessionValidator} y revoca sus
 * refresh tokens (además, un refresh con otra versión ya no vale). Llamar después de {@code profiles.update(...)}.
 */
@ApplicationScoped
public class AccountSessions {

    @Inject SystemTx system;
    @Inject RefreshTokenRepository refreshTokens;
    @Inject SessionValidator validator;
    @Inject Event<SessionsRevoked> revoked;

    public void revoked(UUID profileId) {
        validator.invalidate(profileId);
        // SystemTx: refresh_tokens no es accesible para logyx_app (V14). Si la transacción externa se revierte, los
        // refresh quedan revocados igual: es conservador (el usuario vuelve a iniciar sesión).
        system.run(() -> refreshTokens.revokeAllOf(profileId, OffsetDateTime.now()));
        revoked.fire(new SessionsRevoked(profileId));
    }
}
