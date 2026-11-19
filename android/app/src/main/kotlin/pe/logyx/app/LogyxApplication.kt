package pe.logyx.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pe.logyx.core.data.dataModule
import pe.logyx.feature.auth.authModule
import pe.logyx.feature.driver.driverModule
import pe.logyx.feature.loads.loadsModule
import pe.logyx.feature.requests.requestsModule
import pe.logyx.feature.shipments.shipmentsModule
import pe.logyx.feature.negotiation.negotiationModule
import pe.logyx.feature.incidents.incidentsModule
import pe.logyx.feature.notifications.notificationsModule
import pe.logyx.feature.reputation.reputationModule
import pe.logyx.feature.documents.documentsModule
import pe.logyx.feature.trips.tripsModule

/** Configuración de build expuesta por DI (evita que los features dependan de BuildConfig). */
class AppConfig(
    val apiBaseUrl: String,
    val webBaseUrl: String,
    val versionName: String,
    val debug: Boolean,
    /** Chips de cuentas demo en el login (debug y el APK de pruebas `staging`). */
    val demoAccounts: Boolean = debug,
)

class LogyxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LogyxApplication)
            modules(
                module { single { AppConfig(BuildConfig.API_BASE_URL, BuildConfig.WEB_BASE_URL, BuildConfig.VERSION_NAME, BuildConfig.DEBUG, BuildConfig.DEMO_ACCOUNTS) } },
                dataModule(BuildConfig.API_BASE_URL),
                authModule, loadsModule, requestsModule, shipmentsModule, driverModule,
                negotiationModule, incidentsModule, notificationsModule, reputationModule, documentsModule, tripsModule,
            )
        }
    }
}
