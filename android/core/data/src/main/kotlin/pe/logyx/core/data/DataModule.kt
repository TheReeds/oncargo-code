package pe.logyx.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.logyx.core.network.LogyxApi
import pe.logyx.core.network.createLogyxHttpClient

/**
 * Módulo Koin de datos. [baseUrl] viene de BuildConfig en `:app` (ver android/AGENTS.md para apuntar a otra API).
 */
fun dataModule(baseUrl: String) = module {
    single { TokenHolder() }
    single { SessionStore(androidContext()) }
    single(createdAtStart = true) {
        // Referencia perezosa al repositorio para cerrar sesión ante un 401 sin crear un ciclo de dependencias.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        DefaultAuthRepository(
            api = { get<LogyxApi>() },
            store = get(),
            tokenHolder = get(),
            scope = appScope,
        ) as AuthRepository
    }
    single {
        val tokens = get<TokenHolder>()
        val client = createLogyxHttpClient(baseUrl, tokenProvider = { tokens.token })
        LogyxApi(client, onUnauthorized = {
            // Solo cierra sesión si había una; evita bucles en el login con credenciales inválidas.
            if (tokens.token != null) {
                CoroutineScope(Dispatchers.Main.immediate).launch { get<AuthRepository>().logout() }
            }
        })
    }
    single<CatalogRepository> { DefaultCatalogRepository(get()) }
    single<LoadsRepository> { DefaultLoadsRepository(get()) }
    single<RequestsRepository> { DefaultRequestsRepository(get()) }
    single<ShipmentsRepository> { DefaultShipmentsRepository(get()) }
    single<DocumentsRepository> { DefaultDocumentsRepository(get()) }
    single<IncidentsRepository> { DefaultIncidentsRepository(get()) }
    single<NegotiationRepository> { DefaultNegotiationRepository(get()) }
    single<ReputationRepository> { DefaultReputationRepository(get()) }
    single<NotificationsRepository> { DefaultNotificationsRepository(get()) }
    single<TripsRepository> { DefaultTripsRepository(get()) }
    single<SmartLoadsRepository> { DefaultSmartLoadsRepository(get()) }
    single<EvidenceRepository> { DefaultEvidenceRepository(get(), java.io.File(androidContext().filesDir, "evidence")) }
}
