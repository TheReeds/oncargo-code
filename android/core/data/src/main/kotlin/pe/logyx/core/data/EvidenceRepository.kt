package pe.logyx.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.logyx.core.model.DocumentType
import pe.logyx.core.network.LogyxApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class EvidenceState { QUEUED, UPLOADING, UPLOADED, FAILED }

/**
 * Una evidencia (foto de entrega o firma). [id] es estable: el nombre de archivo de la cola o el id del documento del servidor.
 * [bytes] es la imagen (para miniaturas); null para documentos ya subidos en otra sesión (se descargan por [documentId]).
 */
data class EvidenceItem(
    val id: String,
    val shipmentId: String,
    val stopId: String?,
    val type: DocumentType,
    val state: EvidenceState,
    val progress: Float = 0f,
    val error: String? = null,
    val documentId: String? = null,
    val bytes: ByteArray? = null,
) {
    override fun equals(other: Any?) = other is EvidenceItem && id == other.id && state == other.state &&
        progress == other.progress && error == other.error && documentId == other.documentId
    override fun hashCode() = id.hashCode() * 31 + state.hashCode()
}

/**
 * Evidencia de entrega con cola persistente (conexión intermitente): cada captura se comprime (JPEG ≤ 2 MB), se guarda
 * en disco y se sube; si falla queda en la cola (`FAILED`) y se reintenta con [retryAll]/[retry]. Sobrevive a cierres de la app.
 */
interface EvidenceRepository {
    /** Estado de todas las evidencias conocidas (cola local + subidas). */
    val items: StateFlow<List<EvidenceItem>>

    /** Carga la cola persistida y los documentos ya subidos del envío. */
    suspend fun load(shipmentId: String): Result<Unit>

    /** Comprime, guarda en la cola y empieza a subir. [mime] `image/jpeg` o `image/png`. Devuelve al terminar el intento de subida. */
    suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String = "image/jpeg"): EvidenceItem

    suspend fun retry(id: String)
    suspend fun retryAll(shipmentId: String)

    /** Descarta una evidencia que aún no se subió. */
    suspend fun discard(id: String)
}

/** Compresión JPEG ≤ [maxBytes] y lado mayor ≤ [maxSide]. */
object ImageCompressor {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_SIDE = 2048

    fun compressJpeg(source: ByteArray, maxBytes: Int = MAX_BYTES, maxSide: Int = MAX_SIDE): ByteArray {
        if (source.size <= maxBytes && isSmallEnough(source, maxSide)) return source
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        var bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return source
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= maxBytes || quality <= 40) return out.toByteArray()
            quality -= 10
        }
    }

    private fun isSmallEnough(source: ByteArray, maxSide: Int): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, o)
        return maxOf(o.outWidth, o.outHeight) <= maxSide
    }
}

class DefaultEvidenceRepository(
    private val api: LogyxApi,
    private val dir: File,
    private val compress: (ByteArray) -> ByteArray = { ImageCompressor.compressJpeg(it) },
) : EvidenceRepository {
    private val _items = MutableStateFlow<List<EvidenceItem>>(emptyList())
    override val items: StateFlow<List<EvidenceItem>> = _items.asStateFlow()
    private val lock = Mutex()

    // Nombre de archivo de la cola: {shipmentId}__{stopId}__{TYPE}__{uuid}.{jpg|png}
    private fun fileFor(shipmentId: String, stopId: String, type: DocumentType, mime: String): File {
        val ext = if (mime == "image/png") "png" else "jpg"
        return File(dir, "${shipmentId}__${stopId}__${type.name}__${UUID.randomUUID()}.$ext")
    }

    private fun parse(file: File): EvidenceItem? {
        val parts = file.nameWithoutExtension.split("__")
        if (parts.size != 4) return null
        val type = runCatching { DocumentType.valueOf(parts[2]) }.getOrNull() ?: return null
        return EvidenceItem(file.name, parts[0], parts[1], type, EvidenceState.QUEUED, bytes = null)
    }

    override suspend fun load(shipmentId: String): Result<Unit> {
        val queued = withContext(Dispatchers.IO) {
            dir.mkdirs()
            dir.listFiles().orEmpty().mapNotNull { parse(it) }.filter { it.shipmentId == shipmentId }
                .map { it.copy(bytes = File(dir, it.id).readBytes()) }
        }
        val remote = apiCall { api.shipmentDocuments(shipmentId) }
        _items.update { current ->
            val local = current.filter { it.shipmentId == shipmentId && it.state != EvidenceState.UPLOADED }
            // Un item que ya está en memoria (p. ej. subido mientras se leía el disco) manda sobre la copia leída de la cola.
            val merged = queued.mapNotNull { q ->
                local.firstOrNull { it.id == q.id } ?: q.takeIf { current.none { c -> c.id == q.id } }
            }
            val uploaded = remote.getOrNull().orEmpty()
                .filter { it.type == DocumentType.DELIVERY_PHOTO || it.type == DocumentType.SIGNATURE }
                .map { d ->
                    val prev = current.firstOrNull { it.documentId == d.id }
                    EvidenceItem(d.id, shipmentId, d.stopId, d.type, EvidenceState.UPLOADED, 1f, documentId = d.id, bytes = prev?.bytes)
                }
            // Las subidas de esta sesión con bytes (aún no listadas) se conservan.
            val sessionUploaded = current.filter { it.shipmentId == shipmentId && it.state == EvidenceState.UPLOADED && uploaded.none { u -> u.documentId == it.documentId } }
            current.filter { it.shipmentId != shipmentId } + merged + uploaded + sessionUploaded
        }
        return remote.map { }
    }

    override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String): EvidenceItem {
        val (file, bytes) = withContext(Dispatchers.IO) {
            dir.mkdirs()
            val data = if (mime == "image/jpeg") compress(source) else source
            val f = fileFor(shipmentId, stopId, type, mime)
            f.writeBytes(data)
            f to data
        }
        val item = EvidenceItem(file.name, shipmentId, stopId, type, EvidenceState.QUEUED, bytes = bytes)
        _items.update { it + item }
        upload(item.id)
        return _items.value.firstOrNull { it.id == item.id } ?: item
    }

    private suspend fun upload(id: String) {
        val item = lock.withLock {
            val cur = _items.value.firstOrNull { it.id == id } ?: return
            if (cur.state == EvidenceState.UPLOADING || cur.state == EvidenceState.UPLOADED) return
            patch(id) { it.copy(state = EvidenceState.UPLOADING, progress = 0f, error = null) }
            cur
        }
        val file = File(dir, id)
        val bytes = item.bytes ?: withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
        if (bytes == null) { patch(id) { it.copy(state = EvidenceState.FAILED, error = "No encontramos la foto guardada.") }; return }
        val mime = if (id.endsWith(".png")) "image/png" else "image/jpeg"
        val result = apiCall {
            api.uploadDocument(
                item.shipmentId, bytes, if (item.type == DocumentType.SIGNATURE) "firma.png" else "entrega-${id.takeLast(10)}",
                mime, item.type, item.stopId,
            ) { sent, total -> if (total != null && total > 0) patch(id) { it.copy(progress = (sent.toFloat() / total).coerceIn(0f, 1f)) } }
        }
        result.fold(
            onSuccess = { doc ->
                withContext(Dispatchers.IO) { file.delete() }
                patch(id) { it.copy(state = EvidenceState.UPLOADED, progress = 1f, documentId = doc.id, error = null) }
            },
            onFailure = { e -> patch(id) { it.copy(state = EvidenceState.FAILED, error = e.userMessage()) } },
        )
    }

    private fun patch(id: String, f: (EvidenceItem) -> EvidenceItem) = _items.update { list -> list.map { if (it.id == id) f(it) else it } }

    override suspend fun retry(id: String) = upload(id)

    override suspend fun retryAll(shipmentId: String) {
        _items.value.filter { it.shipmentId == shipmentId && (it.state == EvidenceState.FAILED || it.state == EvidenceState.QUEUED) }
            .forEach { upload(it.id) }
    }

    override suspend fun discard(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.state == EvidenceState.UPLOADED) return
        withContext(Dispatchers.IO) { File(dir, id).delete() }
        _items.update { l -> l.filter { it.id != id } }
    }
}
