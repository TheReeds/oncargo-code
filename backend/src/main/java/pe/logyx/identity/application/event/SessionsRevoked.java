package pe.logyx.identity.application.event;

import java.util.UUID;

/**
 * Subió la versión de sesión de un usuario (evento CDI local, no outbox): {@code SessionValidator} vacía su caché al
 * terminar la transacción. Con varias instancias, las demás se enteran al vencer su caché (logyx.session.cache-ttl).
 */
public record SessionsRevoked(UUID profileId) {}
