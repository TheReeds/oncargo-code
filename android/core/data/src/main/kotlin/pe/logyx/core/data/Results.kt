package pe.logyx.core.data

import kotlinx.coroutines.CancellationException
import pe.logyx.core.model.AccessBlock
import pe.logyx.core.network.ApiException

/** Ejecuta una llamada de red y la envuelve en [Result] sin tragarse la cancelación de corrutinas. */
suspend inline fun <T> apiCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Mensaje en español apto para mostrar al usuario. Los bloqueos de RN01 (`ORG-003`/`ORG-004`) usan el texto de la app
 * (marca OnCargo y a dónde ir); el resto, el del backend.
 */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> accessBlock?.takeIf { it.isOrganizationBlock }?.defaultMessage ?: message
    else -> "Ocurrió un error inesperado. Inténtalo de nuevo."
}

/** Código de error del backend (`LOGYX-XXX-NNN`) si existe. */
val Throwable.apiCode: String? get() = (this as? ApiException)?.code

/**
 * Manejador común de los 403 que bloquean una acción por la cuenta o el rol: `ORG-003` (sin verificar), `ORG-004`
 * (suspendida), `AUTH-007` (rol Lector), `AUTH-008` (solo ADMIN). Úsalo en ofertar, aceptar, reservar, publicar viaje,
 * asignar, etc.: `e.accessBlock?.let { …banner/diálogo… } ?: UiMessage.error(e.userMessage())`.
 */
val Throwable.accessBlock: AccessBlock? get() = AccessBlock.fromCode(apiCode)

/** El error es un 403 de bloqueo de cuenta o de rol (ver [accessBlock]). */
val Throwable.isAccessBlocked: Boolean get() = accessBlock != null
