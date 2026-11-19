package pe.logyx.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

// Sincronización de la cola offline fuera de la pantalla (roadmap 2.5): WorkManager con red y aviso de red recuperada.

/**
 * Vacía la cola del conductor en segundo plano cuando hay red (también tras cerrar la app o reiniciar el teléfono). Usa las
 * mismas instancias de Koin que la app (la cola serializa los envíos con un candado: nunca sale lo mismo dos veces a la vez).
 */
class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val queue: DriverActionQueue by inject()
    private val evidence: EvidenceRepository by inject()

    override suspend fun doWork(): Result {
        evidence.restoreQueued()
        queue.restore()
        val r = queue.flush()
        return if (r.stall == QueueStall.NETWORK) Result.retry() else Result.success()
    }
}

/** Programa [OfflineSyncWorker] (único: reemplaza al anterior) con restricción de red y espera exponencial del sistema. */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    override fun schedule() {
        runCatching {
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    companion object {
        const val WORK_NAME = "logyx-offline-sync"
    }
}

/** Avisa a la cola cuando el sistema recupera una red: reintento inmediato sin esperar el turno de la espera exponencial. */
class NetworkMonitor(context: Context, private val onAvailable: () -> Unit) {
    init {
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onAvailable()
            })
        }
    }
}
