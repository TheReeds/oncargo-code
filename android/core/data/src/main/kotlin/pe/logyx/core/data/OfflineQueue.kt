package pe.logyx.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.ProposeAdjustmentRequest
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopEventRequest
import pe.logyx.core.model.TrackingErrors
import pe.logyx.core.network.ApiException
import pe.logyx.core.network.LogyxJson
import java.time.Clock
import java.util.UUID
import kotlin.random.Random

// Cola offline completa del conductor (Ola 2, zona X; roadmap v2 2.5). Evoluciona la cola de arrive/complete de la Ola 1:
// un único archivo JSON (`filesDir/driver/actions.json`, mismo formato + campos nuevos) con llegadas, cierres (peso y quién
// recibe), reportes de emergencia y propuestas de ajuste; las fotos siguen en la cola en disco de EvidenceRepository y esta
// cola decide cuándo subirlas (después de la llegada y antes del cierre de su parada).

/** Qué guarda una operación pendiente. Los valores de la Ola 1 (`ARRIVE`, `COMPLETE`) se leen igual del disco. */
@Serializable
enum class OperationKind { ARRIVE, COMPLETE, INCIDENT, ADJUSTMENT }

/** Nombre de la Ola 1 (llegar/cerrar parada). */
typealias StopActionOp = OperationKind

/**
 * Algo que el conductor hizo y el servidor aún no confirmó. Las de parada ([isStopOp]) llevan [stopId] y [occurredAt] (hora
 * del dispositivo; null tras un `TRK-008`: cuenta la del servidor). Un cierre espera a que suba la evidencia local de su
 * parada ([waitsForEvidence]); [needsPhoto]/[photoType] = la foto que el servidor exige (entrega: `DELIVERY_PHOTO`, RN06;
 * recojo con `pickupPhotoRequired`: `PICKUP_PHOTO`, `TRK-009`). [idempotencyKey] se genera al encolar y se repite en cada
 * reintento (incidencia y ajuste: el servidor devuelve lo ya creado).
 */
@Serializable
data class PendingOperation(
    val id: String,
    val shipmentId: String,
    val stopId: String? = null,
    val op: OperationKind,
    val occurredAt: String? = null,
    val createdAt: String,
    val needsPhoto: Boolean = false,
    val ownerId: String? = null,
    val photoType: DocumentType? = null,
    val weightKg: Double? = null,
    val receivedByName: String? = null,
    val receivedByDoc: String? = null,
    val incident: ReportIncidentRequest? = null,
    val adjustment: ProposeAdjustmentRequest? = null,
    val idempotencyKey: String? = null,
) {
    val isStopOp: Boolean get() = op == OperationKind.ARRIVE || op == OperationKind.COMPLETE

    /** Foto obligatoria de este cierre (las colas de la Ola 1 no guardaban el tipo: era la de entrega). */
    val requiredPhoto: DocumentType? get() = if (!needsPhoto) null else photoType ?: DocumentType.DELIVERY_PHOTO
}

/** Nombre de la Ola 1. */
typealias PendingStopAction = PendingOperation

/** Por qué la cola se detuvo sin vaciarse. */
enum class QueueStall {
    /** Sin red, 5xx, 429 o sesión no renovable ahora: se reintenta con espera exponencial (5 s → 5 min). */
    NETWORK,

    /** Un cierre espera a que suba la evidencia de su parada (y las paradas siguientes esperan por RN08). */
    WAITING_PHOTO,
}

/** Operación descartada por un error de negocio definitivo (`TRK-001/003/004/005/006/009/010/011`, `INC-*`, 404, 403…). */
data class QueueRejection(val action: PendingOperation, val error: Throwable)

/**
 * Resultado de [DriverActionQueue.flush]: [latest] = último envío devuelto por el servidor (si hubo algún 200 de parada);
 * [rejection] = el primer descarte; [rejections] = todos.
 */
data class FlushResult(
    val latest: ShipmentView? = null,
    val rejection: QueueRejection? = null,
    val stall: QueueStall? = null,
    val rejections: List<QueueRejection> = listOfNotNull(rejection),
)

/** Fallo que vale la pena reintentar (red, servidor, límite de peticiones); el resto es definitivo. */
val Throwable.isRetryable: Boolean
    get() = when (this) {
        is ApiException -> status == 0 || status == 401 || status == 408 || status == 429 || status >= 500 || code == "PARSE"
        is UnsupportedOperationException -> false
        else -> true
    }

/**
 * Espera exponencial con tope y jitter: `base · 2^(n−1)` acotado a [maxMs], menos hasta [jitter] (20 %) al azar para que
 * muchos teléfonos que recuperan señal a la vez no golpeen al servidor juntos. Con `base = 5 s`: 5, 10, 20, 40, 80, 160,
 * 300, 300… s. Nunca pasa de [maxMs].
 */
class Backoff(
    val baseMs: Long = 5_000,
    val maxMs: Long = 300_000,
    private val jitter: Double = 0.2,
    private val random: () -> Double = { Random.nextDouble() },
) {
    /** Espera antes del intento siguiente tras [failures] fallos seguidos (1 = primer fallo). */
    fun delayFor(failures: Int): Long {
        if (failures <= 0) return 0
        val shift = (failures - 1).coerceAtMost(30)
        val exp = (baseMs.toDouble() * (1L shl shift)).coerceAtMost(maxMs.toDouble())
        val r = random().coerceIn(0.0, 1.0)
        return (exp * (1 - jitter * r)).toLong().coerceIn(1, maxMs)
    }
}

/** Programa la sincronización en segundo plano (WorkManager con red). En tests y previews no hace nada. */
fun interface SyncScheduler {
    fun schedule()

    companion object {
        val NONE = SyncScheduler { }
    }
}

/**
 * Estado visible de la cola: [operations] en orden; [sendingId] la que sale ahora; [stall] por qué se detuvo el último
 * intento; [failures] fallos de red seguidos; [nextAttemptAt] (epoch ms) próximo intento automático; [lastError] el último
 * error de red. La evidencia pendiente se suma con [syncItems].
 */
data class SyncStatus(
    val operations: List<PendingOperation> = emptyList(),
    val sendingId: String? = null,
    val stall: QueueStall? = null,
    val failures: Int = 0,
    val nextAttemptAt: Long? = null,
    val lastError: String? = null,
)

enum class SyncItemKind { ARRIVE, COMPLETE, EVIDENCE, INCIDENT, ADJUSTMENT }

/** `QUEUED` en cola · `SENDING` enviando · `WAITING_SIGNAL` esperando señal · `WAITING_PHOTO` espera su foto · `FAILED` falló (con motivo). */
enum class SyncItemState { QUEUED, SENDING, WAITING_SIGNAL, WAITING_PHOTO, FAILED }

/** Una fila de «acciones por sincronizar»: la operación o la evidencia de la que sale, para su etiqueta. */
data class SyncItem(
    val id: String,
    val kind: SyncItemKind,
    val shipmentId: String,
    val stopId: String?,
    val state: SyncItemState,
    val error: String? = null,
    val operation: PendingOperation? = null,
    val evidence: EvidenceItem? = null,
)

/** Evidencia de parada que todavía tiene que subir (en cola, subiendo o fallida); las cerradas ya se explicaron aparte. */
val EvidenceItem.pendingUpload: Boolean
    get() = type in STOP_EVIDENCE_TYPES && (state == EvidenceState.QUEUED || state == EvidenceState.UPLOADING || state == EvidenceState.FAILED)

/**
 * Todo lo que falta sincronizar (para el indicador «3 acciones por sincronizar» y su hoja): la evidencia local primero (sale
 * antes que el cierre de su parada) y luego las operaciones en orden.
 */
fun syncItems(status: SyncStatus, evidence: List<EvidenceItem>): List<SyncItem> {
    val waitingSignal = status.stall == QueueStall.NETWORK
    val photos = evidence.filter { it.pendingUpload }.map { e ->
        val state = when {
            e.state == EvidenceState.UPLOADING -> SyncItemState.SENDING
            e.state == EvidenceState.FAILED && e.offline -> SyncItemState.WAITING_SIGNAL
            e.state == EvidenceState.FAILED -> SyncItemState.FAILED
            waitingSignal -> SyncItemState.WAITING_SIGNAL
            else -> SyncItemState.QUEUED
        }
        SyncItem(e.id, SyncItemKind.EVIDENCE, e.shipmentId, e.stopId, state, error = e.error?.takeIf { state == SyncItemState.FAILED }, evidence = e)
    }
    val ops = status.operations.map { op ->
        val kind = when (op.op) {
            OperationKind.ARRIVE -> SyncItemKind.ARRIVE
            OperationKind.COMPLETE -> SyncItemKind.COMPLETE
            OperationKind.INCIDENT -> SyncItemKind.INCIDENT
            OperationKind.ADJUSTMENT -> SyncItemKind.ADJUSTMENT
        }
        val state = when {
            op.id == status.sendingId -> SyncItemState.SENDING
            op.op == OperationKind.COMPLETE && op.waitsForEvidence(evidence) -> SyncItemState.WAITING_PHOTO
            waitingSignal -> SyncItemState.WAITING_SIGNAL
            else -> SyncItemState.QUEUED
        }
        SyncItem(op.id, kind, op.shipmentId, op.stopId, state, operation = op)
    }
    return photos + ops
}

/**
 * El cierre no sale mientras quede evidencia local de su parada por subir (RN06; después del cierre el servidor la rechaza
 * con `DOC-008`): cualquier foto, firma o guía en cola o esperando señal, y la foto obligatoria ([PendingOperation.requiredPhoto])
 * si falló por el servidor y no hay otra subida. Sin evidencia local pendiente se manda y el servidor decide.
 */
fun PendingOperation.waitsForEvidence(evidence: List<EvidenceItem>): Boolean {
    if (op != OperationKind.COMPLETE) return false
    val mine = evidence.filter { it.shipmentId == shipmentId && it.type in STOP_EVIDENCE_TYPES && (it.stopId == null || it.stopId == stopId) }
    if (mine.any { it.awaitingSignal }) return true
    val required = requiredPhoto ?: return false
    val photos = mine.filter { it.type == required }
    if (photos.any { it.state == EvidenceState.UPLOADED }) return false
    return photos.any { it.state == EvidenceState.FAILED }
}

/** Nombre de la Ola 1. */
fun PendingOperation.waitsForPhoto(evidence: List<EvidenceItem>): Boolean = waitsForEvidence(evidence)

/**
 * Cola persistente (sobrevive al cierre de la app; JSON con [LogyxJson], escritura atómica) de todo lo que el conductor hace
 * sin señal, por dueño (usuario de la sesión). Se envía **en orden por envío** (FIFO):
 * - Llegada → luego la evidencia de esa parada → cierre (con peso y quién recibe). Un cierre con evidencia local pendiente
 *   espera ([waitsForEvidence]) y las paradas siguientes del mismo envío también (RN08); los reportes y ajustes no esperan.
 * - 200 → se quita (idempotente: repetir una llegada/cierre ya aplicado, o un reporte/ajuste con la misma clave, es 200).
 * - 400 `TRK-008` → se repite al momento **sin** `occurredAt`.
 * - Error reintentable ([isRetryable]) → se detiene, cuenta un fallo y el siguiente intento automático espera [Backoff].
 * - Error definitivo → se descarta (en paradas, también las siguientes del mismo envío) y se informa en [FlushResult].
 * Con [scope] corre un bucle de reintento propio (espera exponencial; [retryNow] y [onNetworkAvailable] lo despiertan) y con
 * [scheduler] programa el trabajo en segundo plano. Sin [scope] solo envía cuando se llama a [flush] (tests, previews).
 */
class DriverActionQueue(
    private val repo: ShipmentsRepository,
    private val store: TextStore,
    private val evidence: () -> List<EvidenceItem> = { emptyList() },
    private val currentUserId: () -> String? = { null },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val incidents: IncidentsRepository? = null,
    private val adjustments: AdjustmentsRepository? = null,
    private val uploader: EvidenceRepository? = null,
    private val backoff: Backoff = Backoff(),
    private val scope: CoroutineScope? = null,
    private val scheduler: SyncScheduler = SyncScheduler.NONE,
) {
    private val _pending = MutableStateFlow<List<PendingOperation>>(emptyList())

    /** Operaciones aún no confirmadas por el servidor (de este usuario), en orden de envío. */
    val pending: StateFlow<List<PendingOperation>> = _pending.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus())

    /** Estado visible (conteo, qué sale, esperando señal, próximo intento). */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _results = MutableSharedFlow<FlushResult>(extraBufferCapacity = 8)

    /** Resultados de los envíos del bucle automático (los de [flush] llamados a mano los devuelve la llamada). */
    val results: SharedFlow<FlushResult> = _results.asSharedFlow()

    private val listLock = Mutex()
    private val flushLock = Mutex()
    private var restored = false
    private val serializer = ListSerializer(PendingOperation.serializer())

    // Estado del reintento (en memoria: tras reiniciar la app se intenta enseguida).
    @Volatile private var failures = 0
    @Volatile private var sendingId: String? = null
    @Volatile private var lastStall: QueueStall? = null
    @Volatile private var lastError: String? = null
    @Volatile private var nextAttemptAt: Long? = null

    /** Despierta el bucle para enviar ya (volvió la red). */
    private val wakeNow = Channel<Unit>(Channel.CONFLATED)

    /** Un envío externo (p. ej. el toque del conductor) cambió la espera: el bucle la recalcula desde ahora. */
    private val reschedule = Channel<Unit>(Channel.CONFLATED)
    private val loopGuard = Any()
    private var loopRunning = false

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
        publish()
    }

    private suspend fun persist() = store.write(_pending.value.takeIf { it.isNotEmpty() }?.let { LogyxJson.encodeToString(serializer, it) })

    private suspend fun mutate(f: (List<PendingOperation>) -> List<PendingOperation>) = listLock.withLock {
        restoreLocked()
        _pending.value = f(_pending.value)
        persist()
        publish()
    }

    private fun publish() {
        _status.value = SyncStatus(_pending.value, sendingId, lastStall, failures, nextAttemptAt, lastError)
    }

    /**
     * Registra el toque de llegada o cierre con la hora del dispositivo. Un toque repetido sobre la misma parada y operación
     * no se duplica. En un cierre: [needsPhoto]/[photoType] = foto que exige el servidor; [weightKg] (recojo) y
     * [receivedByName]/[receivedByDoc] (entrega) viajan en el cuerpo.
     */
    suspend fun enqueue(
        shipmentId: String,
        stopId: String,
        op: StopActionOp,
        needsPhoto: Boolean = false,
        photoType: DocumentType? = null,
        weightKg: Double? = null,
        receivedByName: String? = null,
        receivedByDoc: String? = null,
    ): PendingOperation {
        var result: PendingOperation? = null
        mutate { list ->
            val existing = list.firstOrNull { it.shipmentId == shipmentId && it.stopId == stopId && it.op == op }
            if (existing != null) { result = existing; list } else {
                val now = clock.isoNow()
                val action = PendingOperation(
                    newId(), shipmentId, stopId, op, now, now, needsPhoto, currentUserId(),
                    photoType = if (needsPhoto) photoType ?: DocumentType.DELIVERY_PHOTO else null,
                    weightKg = weightKg, receivedByName = receivedByName, receivedByDoc = receivedByDoc,
                )
                result = action
                list + action
            }
        }
        afterEnqueue()
        return result!!
    }

    /**
     * Encola un reporte de incidencia (botón «Emergencia»): la `Idempotency-Key` se genera aquí y se repite en cada intento,
     * así un reintento tras una respuesta perdida devuelve la misma incidencia. [request] lleva `occurredAt` (si no, la hora
     * del dispositivo ahora).
     */
    suspend fun enqueueIncident(shipmentId: String, request: ReportIncidentRequest): PendingOperation {
        val now = clock.isoNow()
        val op = PendingOperation(
            newId(), shipmentId, null, OperationKind.INCIDENT, request.occurredAt ?: now, now, ownerId = currentUserId(),
            incident = request.copy(occurredAt = request.occurredAt ?: now), idempotencyKey = newId(),
        )
        mutate { it + op }
        afterEnqueue()
        return op
    }

    /** Encola una propuesta de ajuste del conductor (2.6) con su `Idempotency-Key` estable. */
    suspend fun enqueueAdjustment(shipmentId: String, request: ProposeAdjustmentRequest): PendingOperation {
        val now = clock.isoNow()
        val op = PendingOperation(
            newId(), shipmentId, request.stopId, OperationKind.ADJUSTMENT, now, now, ownerId = currentUserId(),
            adjustment = request, idempotencyKey = newId(),
        )
        mutate { it + op }
        afterEnqueue()
        return op
    }

    private fun afterEnqueue() {
        scheduler.schedule()
        ensureLoop()
    }

    /** Envía en orden lo que se pueda. Las llamadas concurrentes se serializan (no se manda dos veces lo mismo a la vez). */
    suspend fun flush(): FlushResult = flushLock.withLock { flushLocked() }.also { afterFlush(it) }

    /** «Reintentar ahora» (y volver a primer plano, refrescar, reconectar): olvida la espera acumulada y envía ya. */
    suspend fun retryNow(): FlushResult {
        resetBackoff()
        return flush()
    }

    /** Volvió la red (callback del sistema): reintento inmediato sin esperar el siguiente turno de la espera exponencial. */
    fun onNetworkAvailable() {
        if (_pending.value.isEmpty() && evidence().none { it.pendingUpload }) return
        resetBackoff()
        wakeNow.trySend(Unit)
        ensureLoop()
    }

    private fun resetBackoff() {
        failures = 0
        nextAttemptAt = null
        publish()
    }

    private fun hasWork() = _pending.value.isNotEmpty() || evidence().any { it.pendingUpload && it.state != EvidenceState.UPLOADING }

    private fun afterFlush(r: FlushResult) {
        if (r.stall == QueueStall.NETWORK) {
            failures += 1
            val wait = backoff.delayFor(failures)
            nextAttemptAt = clock.millis() + wait
            scheduler.schedule()
            ensureLoop()
        } else {
            failures = 0
            nextAttemptAt = null
            lastError = null
        }
        lastStall = r.stall
        publish()
        reschedule.trySend(Unit)
    }

    private fun ensureLoop() {
        val sc = scope ?: return
        synchronized(loopGuard) {
            if (loopRunning) return
            loopRunning = true
        }
        sc.launch {
            try { runLoop() } finally { synchronized(loopGuard) { loopRunning = false } }
        }
    }

    /**
     * Bucle de reintento: tras un fallo de red espera lo que diga [Backoff] (sin fallo, el tope: es solo un respaldo) y
     * vuelve a enviar; [onNetworkAvailable] lo adelanta y un envío externo reinicia la espera. Termina sin trabajo.
     */
    private suspend fun runLoop() {
        restore()
        while (true) {
            synchronized(loopGuard) { if (!hasWork()) { loopRunning = false; return } }
            val wait = if (lastStall == QueueStall.NETWORK) backoff.delayFor(failures) else backoff.maxMs
            val signal = withTimeoutOrNull(wait) {
                select<Boolean> {
                    wakeNow.onReceive { true }
                    reschedule.onReceive { false }
                }
            }
            if (signal == false) continue // otra llamada envió: se recalcula la espera desde ahora
            if (!hasWork()) continue
            val r = flush()
            if (r.latest != null || r.rejections.isNotEmpty() || r.stall == null) _results.tryEmit(r)
        }
    }

    private suspend fun flushLocked(): FlushResult {
        restore()
        var latest: ShipmentView? = null
        val rejections = mutableListOf<QueueRejection>()
        var stall: QueueStall? = null
        val blocked = mutableSetOf<String>() // envíos con la cadena de paradas detenida (espera evidencia)
        val held = mutableSetOf<String>() // operaciones que se quedan en este intento
        val tried = mutableSetOf<String>() // evidencia ya intentada en este envío (no se repite en la misma vuelta)
        fun done(s: QueueStall?) = FlushResult(latest, rejections.firstOrNull(), s, rejections.toList()).also { sendingId = null; publish() }

        // 1) Evidencia de paradas sin llegada pendiente en la cola (la de una llegada pendiente sale justo después de ella).
        if (!uploadEvidence(tried) { e -> _pending.value.none { it.op == OperationKind.ARRIVE && it.shipmentId == e.shipmentId && it.stopId == e.stopId } }) {
            return done(QueueStall.NETWORK)
        }
        while (true) {
            val next = _pending.value.firstOrNull { it.id !in held && !(it.isStopOp && it.shipmentId in blocked) } ?: break
            if (next.op == OperationKind.COMPLETE) {
                if (!uploadEvidence(tried) { it.shipmentId == next.shipmentId && (it.stopId == null || it.stopId == next.stopId) }) return done(QueueStall.NETWORK)
                if (next.waitsForEvidence(evidence())) {
                    blocked += next.shipmentId
                    held += next.id
                    stall = QueueStall.WAITING_PHOTO
                    continue
                }
            }
            sendingId = next.id
            publish()
            var action = next
            var result = send(action)
            if (action.isStopOp && action.occurredAt != null && result.exceptionOrNull()?.apiCode == TrackingErrors.BAD_OCCURRED_AT) {
                // La hora del dispositivo no sirve (reloj desfasado, > 48 h, anterior al paso previo): cuenta la del servidor.
                action = action.copy(occurredAt = null)
                mutate { list -> list.map { if (it.id == action.id) action else it } }
                result = send(action)
            }
            val error = result.exceptionOrNull()
            when {
                error == null -> {
                    (result.getOrNull() as? ShipmentView)?.let { latest = it }
                    mutate { list -> list.filter { it.id != action.id } }
                    if (action.op == OperationKind.ARRIVE) {
                        // Ya llegó: la evidencia de esa parada puede subir (antes del cierre).
                        if (!uploadEvidence(tried) { it.shipmentId == action.shipmentId && it.stopId == action.stopId }) return done(QueueStall.NETWORK)
                    }
                }
                error.isRetryable -> {
                    lastError = error.userMessage()
                    return done(QueueStall.NETWORK)
                }
                else -> {
                    rejections += QueueRejection(action, error)
                    // Las siguientes paradas del mismo envío dependían de esta (RN08): ya no son válidas.
                    mutate { list ->
                        val index = list.indexOfFirst { it.id == action.id }
                        list.filterIndexed { i, it ->
                            !(it.id == action.id || (action.isStopOp && it.isStopOp && i > index && it.shipmentId == action.shipmentId))
                        }
                    }
                }
            }
        }
        return done(stall)
    }

    /**
     * Sube la evidencia pendiente que cumple [filter] (en cola o fallida). false = se cortó la red (se detiene el envío).
     * La que el servidor rechaza no detiene la cola (queda `FAILED` o `CLOSED`, con su aviso en la pantalla).
     */
    private suspend fun uploadEvidence(tried: MutableSet<String>, filter: (EvidenceItem) -> Boolean): Boolean {
        val up = uploader ?: return true
        val todo = evidence().filter {
            it.id !in tried && it.type in STOP_EVIDENCE_TYPES && (it.state == EvidenceState.QUEUED || it.state == EvidenceState.FAILED) && filter(it)
        }
        for (item in todo) {
            tried += item.id
            sendingId = item.id
            publish()
            up.retry(item.id)
            val after = evidence().firstOrNull { it.id == item.id }
            if (after != null && after.state == EvidenceState.FAILED && after.offline) {
                lastError = after.error
                return false
            }
        }
        return true
    }

    private suspend fun send(a: PendingOperation): Result<Any?> = when (a.op) {
        OperationKind.ARRIVE -> repo.arrive(a.shipmentId, a.stopId.orEmpty(), a.occurredAt)
        OperationKind.COMPLETE -> repo.completeStop(
            a.shipmentId, a.stopId.orEmpty(), StopEventRequest(a.occurredAt, a.weightKg, a.receivedByName, a.receivedByDoc),
        )
        OperationKind.INCIDENT -> {
            val body = a.incident
            if (incidents == null || body == null) Result.failure(UnsupportedOperationException("Sin repositorio de incidencias"))
            else incidents.report(a.shipmentId, body, a.idempotencyKey)
        }
        OperationKind.ADJUSTMENT -> {
            val body = a.adjustment
            if (adjustments == null || body == null) Result.failure(UnsupportedOperationException("Sin repositorio de ajustes"))
            else adjustments.propose(a.shipmentId, body, a.idempotencyKey)
        }
    }

    /** Borra la cola (p. ej. al cambiar de usuario). */
    suspend fun clear() = mutate { emptyList() }

    companion object {
        fun inMemory(
            repo: ShipmentsRepository,
            evidence: () -> List<EvidenceItem> = { emptyList() },
            clock: Clock = Clock.systemDefaultZone(),
            incidents: IncidentsRepository? = null,
        ) = DriverActionQueue(repo, TextStore.InMemory(), evidence, clock = clock, incidents = incidents)
    }
}
