package pe.logyx.core.data

import pe.logyx.core.model.AdjustmentView
import pe.logyx.core.model.ProposeAdjustmentRequest
import pe.logyx.core.model.ShipmentAdjustmentsView
import pe.logyx.core.network.LogyxApi

/**
 * Ajustes del envío (roadmap 2.6): peso real, estadía, peajes, maniobras. Precio final = acordado + aceptados.
 * El transportista (o su conductor) propone; la PYME acepta o rechaza (con motivo) en 48 h; si no, decide el operador.
 */
interface AdjustmentsRepository {
    suspend fun forShipment(shipmentId: String): Result<ShipmentAdjustmentsView>
    /** Con [idempotencyKey] un reintento devuelve el ajuste ya creado (24 h). */
    suspend fun propose(shipmentId: String, request: ProposeAdjustmentRequest, idempotencyKey: String? = null): Result<AdjustmentView>
    suspend fun accept(id: String, note: String? = null): Result<AdjustmentView>
    /** El motivo es obligatorio (`ADJ-004`, field `note`). */
    suspend fun reject(id: String, note: String): Result<AdjustmentView>
    suspend fun withdraw(id: String): Result<AdjustmentView>

    companion object {
        /** Sin ajustes (tests y previews que no los usan). */
        val NONE: AdjustmentsRepository = object : AdjustmentsRepository {
            private fun <T> no(): Result<T> = Result.failure(UnsupportedOperationException())
            override suspend fun forShipment(shipmentId: String) = no<ShipmentAdjustmentsView>()
            override suspend fun propose(shipmentId: String, request: ProposeAdjustmentRequest, idempotencyKey: String?) = no<AdjustmentView>()
            override suspend fun accept(id: String, note: String?) = no<AdjustmentView>()
            override suspend fun reject(id: String, note: String) = no<AdjustmentView>()
            override suspend fun withdraw(id: String) = no<AdjustmentView>()
        }
    }
}

class DefaultAdjustmentsRepository(private val api: LogyxApi) : AdjustmentsRepository {
    override suspend fun forShipment(shipmentId: String) = apiCall { api.shipmentAdjustments(shipmentId) }
    override suspend fun propose(shipmentId: String, request: ProposeAdjustmentRequest, idempotencyKey: String?) =
        apiCall { api.proposeAdjustment(shipmentId, request.copy(note = request.note?.trim()?.takeIf { it.isNotEmpty() }), idempotencyKey) }
    override suspend fun accept(id: String, note: String?) = apiCall { api.acceptAdjustment(id, note) }
    override suspend fun reject(id: String, note: String) = apiCall { api.rejectAdjustment(id, note) }
    override suspend fun withdraw(id: String) = apiCall { api.withdrawAdjustment(id) }
}
