package pe.logyx.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Crea el cliente HTTP.
 * @param baseUrl URL base de la API, p. ej. `http://10.0.2.2:8080`.
 * @param tokenProvider se evalúa en cada petición (el token puede cambiar tras login/logout).
 */
fun createLogyxHttpClient(
    baseUrl: String,
    tokenProvider: () -> String?,
    engine: HttpClientEngine = OkHttp.create(),
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(LogyxJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
    defaultRequest {
        url(baseUrl.trimEnd('/') + "/")
        accept(ContentType.Application.Json)
        header(HttpHeaders.AcceptLanguage, "es-PE")
        tokenProvider()?.let { bearerAuth(it) }
    }
}
