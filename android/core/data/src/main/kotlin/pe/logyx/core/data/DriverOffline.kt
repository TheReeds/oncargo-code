package pe.logyx.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.network.LogyxJson
import java.io.File
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Conductor sin señal (Ola 1, zona B; roadmap v2 1.11): último viaje en disco y aplicación optimista. La cola vive en OfflineQueue.kt.

/** Hora del dispositivo en ISO-8601 con offset y sin fracciones (`2026-11-18T09:15:02-05:00`). */
fun Clock.isoNow(): String = OffsetDateTime.now(this).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

/**
 * Texto persistido en disco (un archivo JSON). Escritura atómica (archivo temporal + rename) para que un cierre de la app
 * a mitad de escritura no deje el archivo corrupto; si aun así no se puede leer, se trata como vacío.
 */
interface TextStore {
    suspend fun read(): String?
    suspend fun write(text: String?)

    class InFile(private val file: File) : TextStore {
        override suspend fun read(): String? = withContext(Dispatchers.IO) { runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull() }
        override suspend fun write(text: String?) = withContext(Dispatchers.IO) {
            if (text == null) { file.delete(); return@withContext }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            Unit
        }
    }

    class InMemory(var value: String? = null) : TextStore {
        override suspend fun read() = value
        override suspend fun write(text: String?) { value = text }
    }
}

/** Viaje guardado: [savedAt] es la hora (ISO) en que llegó del servidor; [ownerId] el usuario que lo vio. */
@Serializable
data class CachedTrip(val trip: ShipmentView, val savedAt: String, val ownerId: String? = null)

/**
 * Último viaje activo del conductor (roadmap 1.11, criterio 3): se guarda con cada respuesta del servidor y se muestra sin
 * red con «Sin conexión · datos de hh:mm». Solo lo lee el mismo usuario que lo guardó (otro conductor en el mismo equipo no
 * lo ve). `save(null)` lo borra (el servidor dice que no hay viaje activo).
 */
class DriverTripCache(
    private val store: TextStore,
    private val currentUserId: () -> String? = { null },
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun load(): CachedTrip? {
        val cached = store.read()?.let { runCatching { LogyxJson.decodeFromString(CachedTrip.serializer(), it) }.getOrNull() } ?: return null
        val me = currentUserId()
        return cached.takeIf { me == null || it.ownerId == null || it.ownerId == me }
    }

    /** Guarda (o borra con null) y devuelve la hora de guardado. */
    suspend fun save(trip: ShipmentView?): String? {
        if (trip == null) { store.write(null); return null }
        val savedAt = clock.isoNow()
        store.write(LogyxJson.encodeToString(CachedTrip.serializer(), CachedTrip(trip, savedAt, currentUserId())))
        return savedAt
    }

    companion object {
        /** Sin disco (tests, previews): todo en memoria. */
        fun inMemory(clock: Clock = Clock.systemDefaultZone()) = DriverTripCache(TextStore.InMemory(), clock = clock)
    }
}

/**
 * Aplica de forma optimista las acciones pendientes al viaje mostrado: PENDING → ARRIVED → COMPLETED (nunca retrocede lo
 * que el servidor ya confirmó). Cerradas todas las paradas el envío pasa a DELIVERED; con alguna empezada, a IN_TRANSIT.
 */
fun ShipmentView.withPending(actions: List<PendingStopAction>): ShipmentView {
    val mine = actions.filter { it.shipmentId == id && it.isStopOp }
    if (mine.isEmpty() || status == ShipmentStatus.CANCELLED) return this
    var updated = stops
    var lastAt: String? = null
    for (a in mine) {
        if (!a.isStopOp) continue
        val at = a.occurredAt ?: a.createdAt
        updated = updated.map { s ->
            if (s.id != a.stopId) s else when (a.op) {
                StopActionOp.ARRIVE -> if (s.status == StopStatus.PENDING) s.copy(status = StopStatus.ARRIVED, arrivedAt = at) else s
                StopActionOp.COMPLETE -> if (s.status != StopStatus.COMPLETED) {
                    lastAt = at
                    s.copy(
                        status = StopStatus.COMPLETED, arrivedAt = s.arrivedAt ?: at, completedAt = at,
                        loadedWeightKg = a.weightKg ?: s.loadedWeightKg,
                        receivedByName = a.receivedByName ?: s.receivedByName, receivedByDoc = a.receivedByDoc ?: s.receivedByDoc,
                    )
                } else s
                else -> s
            }
        }
    }
    val allDone = updated.isNotEmpty() && updated.all { it.status == StopStatus.COMPLETED }
    val newStatus = when {
        status == ShipmentStatus.DELIVERED -> status
        allDone -> ShipmentStatus.DELIVERED
        status == ShipmentStatus.PENDING && updated.any { it.status != StopStatus.PENDING } -> ShipmentStatus.IN_TRANSIT
        else -> status
    }
    return copy(
        stops = updated, status = newStatus,
        deliveredAt = if (newStatus == ShipmentStatus.DELIVERED && status != ShipmentStatus.DELIVERED) lastAt ?: deliveredAt else deliveredAt,
    )
}
