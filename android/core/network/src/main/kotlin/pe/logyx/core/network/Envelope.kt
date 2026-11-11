package pe.logyx.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parser JSON compartido: tolera campos nuevos del backend. */
val LogyxJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class Meta(val timestamp: String? = null, val page: Int? = null, val size: Int? = null, val total: Int? = null, val nextCursor: String? = null)

/** Respuesta exitosa: `{ "data": ..., "meta": {...} }`. */
@Serializable
data class ApiEnvelope<T>(val data: T, val meta: Meta? = null)

@Serializable
data class ApiError(val code: String? = null, val message: String? = null, val field: String? = null)

/** Respuesta de error: `{ "error": { code, message, field }, "meta": {...} }`. */
@Serializable
data class ApiErrorEnvelope(val error: ApiError? = null, val meta: Meta? = null)

/** Error de la API (o de red con `status = 0`) con mensaje listo para mostrar al usuario. */
class ApiException(
    val status: Int,
    val code: String?,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    val isUnauthorized get() = status == 401
    val isNetwork get() = status == 0
}
