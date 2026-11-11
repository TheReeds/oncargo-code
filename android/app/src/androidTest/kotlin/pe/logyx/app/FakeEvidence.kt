package pe.logyx.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.model.DocumentType

/** Evidencia en memoria: cada `enqueue` queda subida al instante (o fallida si [uploadOk] es false). */
class FakeEvidence(var uploadOk: Boolean = true) : EvidenceRepository {
    private val _items = MutableStateFlow<List<EvidenceItem>>(emptyList())
    override val items: StateFlow<List<EvidenceItem>> = _items
    private var n = 0

    /** Hay al menos una foto de entrega subida para el envío (lo consulta [FakeShipments.complete]). */
    fun hasUploadedPhoto(shipmentId: String) = _items.value.any {
        it.shipmentId == shipmentId && it.type == DocumentType.DELIVERY_PHOTO && it.state == EvidenceState.UPLOADED
    }

    override suspend fun load(shipmentId: String) = Result.success(Unit)

    override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String): EvidenceItem {
        val item = EvidenceItem(
            "fake-${n++}", shipmentId, stopId, type,
            if (uploadOk) EvidenceState.UPLOADED else EvidenceState.FAILED, if (uploadOk) 1f else 0f, bytes = source,
        )
        _items.value = _items.value + item
        return item
    }

    override suspend fun retry(id: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(state = EvidenceState.UPLOADED, progress = 1f) else it }
    }
    override suspend fun retryAll(shipmentId: String) {
        _items.value = _items.value.map { if (it.state == EvidenceState.FAILED) it.copy(state = EvidenceState.UPLOADED, progress = 1f) else it }
    }
    override suspend fun discard(id: String) { _items.value = _items.value.filter { it.id != id } }
}
