package pe.logyx.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.logyx.core.network.LogyxApi
import pe.logyx.core.network.RealtimeClient
import pe.logyx.core.network.SessionRefresher
import pe.logyx.core.network.createLogyxHttpClient

/**
 * Módulo Koin de datos. [baseUrl] viene de BuildConfig en `:app` (ver android/AGENTS.md para apuntar a otra API).
 */
fun dataModule(baseUrl: String) = module {
    single { TokenHolder() }
    single<SecretCipher> { KeystoreSecretCipher() }
    single<SessionStorage> { SessionStore(androidContext(), get()) }
    single(createdAtStart = true) {
        // Referencia perezosa a la API (la API pide el repositorio para renovar la sesión: sin ciclo de dependencias).
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        DefaultAuthRepository(
            api = { get<LogyxApi>() },
            store = get(),
            tokenHolder = get(),
            scope = appScope,
        )
    }
    single<AuthRepository> { get<DefaultAuthRepository>() }
    single<SessionRefresher> { get<DefaultAuthRepository>() }
    single {
        val tokens = get<TokenHolder>()
        createLogyxHttpClient(baseUrl, tokenProvider = { tokens.token })
    }
    single {
        val tokens = get<TokenHolder>()
        // Ante un 401 renueva la sesión con el refresh token (F24) y reintenta; si no se puede, cierra la sesión.
        LogyxApi(get(), onUnauthorized = {
            // Solo cierra sesión si había una; evita bucles en el login con credenciales inválidas.
            if (tokens.token != null) {
                CoroutineScope(Dispatchers.Main.immediate).launch { get<AuthRepository>().logout() }
            }
        }, refresher = { get<SessionRefresher>() })
    }
    // Tiempo real (SSE): misma conexión HTTP/token; un 401 del stream también cierra la sesión.
    single {
        val tokens = get<TokenHolder>()
        RealtimeClient(get(), tokenProvider = { tokens.token }, onUnauthorized = {
            if (tokens.token != null) {
                CoroutineScope(Dispatchers.Main.immediate).launch { get<AuthRepository>().logout() }
            }
        }, refresher = { get<SessionRefresher>() })
    }
    single<RealtimeRepository> {
        DefaultRealtimeRepository(get(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }
    single<BidPreferences> { DataStoreBidPreferences(androidContext()) }
    single<CatalogRepository> { DefaultCatalogRepository(get()) }
    single<LoadsRepository> { DefaultLoadsRepository(get()) }
    single<RequestsRepository> { DefaultRequestsRepository(get()) }
    single<ShipmentsRepository> { DefaultShipmentsRepository(get()) }
    single<DocumentsRepository> { DefaultDocumentsRepository(get()) }
    single<AccessRepository> { DefaultAccessRepository(get()) }
    single<IncidentsRepository> { DefaultIncidentsRepository(get()) }
    single<NegotiationRepository> { DefaultNegotiationRepository(get()) }
    single<ReputationRepository> { DefaultReputationRepository(get()) }
    single<NotificationsRepository> { DefaultNotificationsRepository(get()) }
    single<TripsRepository> { DefaultTripsRepository(get()) }
    single<SmartLoadsRepository> { DefaultSmartLoadsRepository(get()) }
    single<RoutesRepository> { DefaultRoutesRepository(get<LogyxApi>()) }
    single<EvidenceRepository> { DefaultEvidenceRepository(get(), java.io.File(androidContext().filesDir, "evidence")) }
    // Conductor sin señal (roadmap 1.11): último viaje y cola de arrive/complete en filesDir/driver (sobreviven al cierre).
    single {
        val auth = get<AuthRepository>()
        DriverTripCache(TextStore.InFile(java.io.File(androidContext().filesDir, "driver/active-trip.json")), { auth.sessionUser?.id })
    }
    single {
        val auth = get<AuthRepository>()
        val evidence = get<EvidenceRepository>()
        DriverActionQueue(
            repo = get(), store = TextStore.InFile(java.io.File(androidContext().filesDir, "driver/actions.json")),
            evidence = { evidence.items.value }, currentUserId = { auth.sessionUser?.id },
        )
    }
}
