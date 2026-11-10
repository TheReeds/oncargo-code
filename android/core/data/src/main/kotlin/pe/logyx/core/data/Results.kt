package pe.logyx.core.data

import kotlinx.coroutines.CancellationException
import pe.logyx.core.network.ApiException

/** Ejecuta una llamada de red y la envuelve en [Result] sin tragarse la cancelación de corrutinas. */
suspend inline fun <T> apiCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Mensaje en español apto para mostrar al usuario. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    else -> "Ocurrió un error inesperado. Inténtalo de nuevo."
}

/** Código de error del backend (`LOGYX-XXX-NNN`) si existe. */
val Throwable.apiCode: String? get() = (this as? ApiException)?.code
