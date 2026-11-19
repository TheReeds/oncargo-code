package pe.logyx.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.TrackingErrors
import pe.logyx.core.network.ApiException
import pe.logyx.core.network.LogyxJson
import java.io.File
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// Conductor sin señal (Ola 1, zona B; roadmap v2 1.11): último viaje en disco + cola persistente de arrive/complete.

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

enum class StopActionOp { ARRIVE, COMPLETE }

/**
 * Un toque del conductor pendiente de confirmar por el servidor. [occurredAt] = hora del dispositivo al tocar (null tras un
 * `TRK-008`: se manda sin hora y cuenta la del servidor). [needsPhoto]: cierre de entrega; espera a que la foto local suba.
 */
@Serializable
data class PendingStopAction(
    val id: String,
    val shipmentId: String,
    val stopId: String,
    val op: StopActionOp,
    val occurredAt: String?,
    val createdAt: String,
    val needsPhoto: Boolean = false,
    val ownerId: String? = null,
)

/** Por qué la cola se detuvo sin vaciarse. */
enum class QueueStall {
    /** Sin red, 5xx, 429 o sesión no renovable ahora: se reintenta (cada ~20 s, al volver a primer plano o al reconectar). */
    NETWORK,

    /** La primera acción es un cierre de entrega y su foto todavía no subió. */
    WAITING_PHOTO,
}

/** Acción descartada por un error de negocio definitivo (`TRK-001/003/004/005/006`, 404, 403…). */
data class QueueRejection(val action: PendingStopAction, val error: Throwable)

/** Resultado de [DriverActionQueue.flush]: [latest] = último envío devuelto por el servidor (si hubo algún 200). */
data class FlushResult(val latest: ShipmentView? = null, val rejection: QueueRejection? = null, val stall: QueueStall? = null)

/**
 * Una evidencia local de foto de entrega todavía no subida bloquea el envío de su `complete`: la entrega se manda **después**
 * de la foto (RN06 en el servidor, `TRK-006`). Si no hay ninguna foto local pendiente (ya subió, se subió en otra sesión o se
 * descartó) se manda y el servidor decide.
 */
fun PendingStopAction.waitsForPhoto(evidence: List<EvidenceItem>): Boolean {
    if (!needsPhoto) return false
    val photos = evidence.filter { it.shipmentId == shipmentId && it.type == DocumentType.DELIVERY_PHOTO && (it.stopId == null || it.stopId == stopId) }
    if (photos.any { it.state == EvidenceState.UPLOADED }) return false
    return photos.any { it.state == EvidenceState.QUEUED || it.state == EvidenceState.UPLOADING || it.state == EvidenceState.FAILED }
}

/** Fallo que vale la pena reintentar (red, servidor, límite de peticiones); el resto es definitivo. */
val Throwable.isRetryable: Boolean
    get() = when (this) {
        is ApiException -> status == 0 || status == 401 || status == 408 || status == 429 || status >= 500 || code == "PARSE"
        else -> true
    }

/**
 * Cola persistente (sobrevive al cierre de la app) de llegadas y cierres de parada del conductor. Se envía en orden FIFO:
 * - 200 → se quita y el envío del servidor manda (idempotente: repetir una acción ya aplicada también es 200).
 * - 400 `TRK-008` (hora del dispositivo no aceptada) → se repite al momento **sin** `occurredAt`.
 * - Error reintentable ([isRetryable]) → se detiene y queda en la cola.
 * - Error definitivo → se descarta esa acción y las siguientes del mismo envío (dependían de ella) y se informa.
 * Un `complete` de entrega no sale mientras su foto local no haya subido ([waitsForPhoto]).
 */
class DriverActionQueue(
    private val repo: ShipmentsRepository,
    private val store: TextStore,
    private val evidence: () -> List<EvidenceItem> = { emptyList() },
    private val currentUserId: () -> String? = { null },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _pending = MutableStateFlow<List<PendingStopAction>>(emptyList())

    /** Acciones aún no confirmadas por el servidor (de este usuario), en orden de envío. */
    val pending: StateFlow<List<PendingStopAction>> = _pending.asStateFlow()

    private val listLock = Mutex()
    private val flushLock = Mutex()
    private var restored = false
    private val serializer = ListSerializer(PendingStopAction.serializer())

    /** Lee la cola del disco (una vez); descarta las de otro usuario que haya usado el equipo. */
    suspend fun restore() = listLock.withLock { restoreLocked() }

    private suspend fun restoreLocked() {
        if (restored) return
        val saved = store.read()?.let { runCatching { LogyxJson.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        val me = currentUserId()
        val mine = saved.filter { me == null || it.ownerId == null || it.ownerId == me }
        _pending.value = mine
        restored = true
        if (mine.size != saved.size) persist()
    }

    private suspend fun persist() = store.write(_pending.value.takeIf { it.isNotEmpty() }?.let { LogyxJson.encodeToString(serializer, it) })

    private suspend fun mutate(f: (List<PendingStopAction>) -> List<PendingStopAction>) = listLock.withLock {
        restoreLocked()
        _pending.value = f(_pending.value)
        persist()
    }

    /** Registra el toque con la hora del dispositivo. Un toque repetido sobre la misma parada y operación no se duplica. */
    suspend fun enqueue(shipmentId: String, stopId: String, op: StopActionOp, needsPhoto: Boolean = false): PendingStopAction {
        var result: PendingStopAction? = null
        mutate { list ->
            val existing = list.firstOrNull { it.shipmentId == shipmentId && it.stopId == stopId && it.op == op }
            if (existing != null) { result = existing; list } else {
                val now = clock.isoNow()
                val action = PendingStopAction(newId(), shipmentId, stopId, op, now, now, needsPhoto, currentUserId())
                result = action
                list + action
            }
        }
        return result!!
    }

    /** Envía en orden lo que se pueda. Las llamadas concurrentes se serializan (no se manda dos veces lo mismo a la vez). */
    suspend fun flush(): FlushResult = flushLock.withLock { flushLocked() }

    private suspend fun flushLocked(): FlushResult {
        restore()
        var latest: ShipmentView? = null
        var rejection: QueueRejection? = null
        while (true) {
            val head = _pending.value.firstOrNull() ?: return FlushResult(latest, rejection, null)
            if (head.waitsForPhoto(evidence())) return FlushResult(latest, rejection, QueueStall.WAITING_PHOTO)
            var action = head
            var result = send(action)
            if (action.occurredAt != null && result.exceptionOrNull()?.apiCode == TrackingErrors.BAD_OCCURRED_AT) {
                // La hora del dispositivo no sirve (reloj desfasado, > 48 h, anterior al paso previo): cuenta la del servidor.
                action = action.copy(occurredAt = null)
                mutate { list -> list.map { if (it.id == action.id) action else it } }
                result = send(action)
            }
            val error = result.exceptionOrNull()
            when {
                error == null -> {
                    latest = result.getOrNull()
                    mutate { list -> list.filter { it.id != action.id } }
                }
                error.isRetryable -> return FlushResult(latest, rejection, QueueStall.NETWORK)
                else -> {
                    if (rejection == null) rejection = QueueRejection(action, error)
                    // Las siguientes del mismo envío dependían de esta (RN08): ya no son válidas.
                    mutate { list ->
                        val index = list.indexOfFirst { it.id == action.id }
                        list.filterIndexed { i, it -> !(it.id == action.id || (i > index && it.shipmentId == action.shipmentId)) }
                    }
                }
            }
        }
    }

    private suspend fun send(a: PendingStopAction): Result<ShipmentView> = when (a.op) {
        StopActionOp.ARRIVE -> repo.arrive(a.shipmentId, a.stopId, a.occurredAt)
        StopActionOp.COMPLETE -> repo.complete(a.shipmentId, a.stopId, a.occurredAt)
    }

    /** Borra la cola (p. ej. al cambiar de usuario). */
    suspend fun clear() = mutate { emptyList() }

    companion object {
        fun inMemory(repo: ShipmentsRepository, evidence: () -> List<EvidenceItem> = { emptyList() }, clock: Clock = Clock.systemDefaultZone()) =
            DriverActionQueue(repo, TextStore.InMemory(), evidence, clock = clock)
    }
}

/**
 * Aplica de forma optimista las acciones pendientes al viaje mostrado: PENDING → ARRIVED → COMPLETED (nunca retrocede lo
 * que el servidor ya confirmó). Cerradas todas las paradas el envío pasa a DELIVERED; con alguna empezada, a IN_TRANSIT.
 */
fun ShipmentView.withPending(actions: List<PendingStopAction>): ShipmentView {
    val mine = actions.filter { it.shipmentId == id }
    if (mine.isEmpty() || status == ShipmentStatus.CANCELLED) return this
    var updated = stops
    var lastAt: String? = null
    for (a in mine) {
        val at = a.occurredAt ?: a.createdAt
        updated = updated.map { s ->
            if (s.id != a.stopId) s else when (a.op) {
                StopActionOp.ARRIVE -> if (s.status == StopStatus.PENDING) s.copy(status = StopStatus.ARRIVED, arrivedAt = at) else s
                StopActionOp.COMPLETE -> if (s.status != StopStatus.COMPLETED) {
                    lastAt = at
                    s.copy(status = StopStatus.COMPLETED, arrivedAt = s.arrivedAt ?: at, completedAt = at)
                } else s
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
