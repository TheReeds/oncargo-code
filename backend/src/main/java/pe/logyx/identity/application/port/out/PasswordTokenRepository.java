package pe.logyx.identity.application.port.out;

import pe.logyx.identity.domain.model.PasswordToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Tokens de contraseña. Solo desde {@code SystemTx}: logyx_app no tiene privilegios sobre la tabla (V14). */
public interface PasswordTokenRepository {
    void insert(PasswordToken token);
    /** Busca por hash y bloquea la fila (un token no se canjea dos veces en paralelo). */
    Optional<PasswordToken> findByHashForUpdate(String tokenHash);
    Optional<PasswordToken> findByHash(String tokenHash);
    void update(PasswordToken token);
    /** Invalida los tokens sin usar del usuario (al emitir otro o al definir la contraseña). */
    int revokeUnused(UUID profileId, OffsetDateTime now);
    /** Limpieza: borra los vencidos antes de {@code cutoff}. */
    int deleteExpiredBefore(OffsetDateTime cutoff);
}
