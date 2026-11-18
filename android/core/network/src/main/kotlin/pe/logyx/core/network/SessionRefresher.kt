package pe.logyx.core.network

/** Qué hacer con una petición que recibió 401 tras intentar renovar la sesión. */
enum class RefreshOutcome {
    /** Hay un access token nuevo (esta llamada o una concurrente lo renovó): repite la petición original. */
    RETRY,

    /** No hay forma de seguir (sin refresh token, `AUTH-009`, sesión revocada): la sesión ya se cerró. */
    SESSION_ENDED,

    /** No se pudo renovar por un problema transitorio (sin red, 5xx): conserva la sesión y reporta error de red. */
    UNAVAILABLE,
}

/**
 * Renueva la sesión del móvil con `POST /auth/refresh` ante un 401 (F24: el conductor no debe perder la sesión en ruta).
 * La implementación (en `:core:data`) debe serializar las renovaciones: el refresh token rota en cada uso y reutilizar
 * uno ya usado revoca toda la sesión del dispositivo.
 */
interface SessionRefresher {
    /** [rejectedToken] es el access token con el que se hizo la petición rechazada (null si no llevaba ninguno). */
    suspend fun refreshAfterUnauthorized(rejectedToken: String?): RefreshOutcome
}
