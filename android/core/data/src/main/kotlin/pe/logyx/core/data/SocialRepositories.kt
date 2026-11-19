package pe.logyx.core.data

import pe.logyx.core.model.ChatThreadView
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.CursorPage
import pe.logyx.core.model.DocumentView
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentView
import pe.logyx.core.model.MessageView
import pe.logyx.core.model.NotificationView
import pe.logyx.core.model.Page
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.ReputationView
import pe.logyx.core.model.ReviewView
import pe.logyx.core.model.ShipmentReviewsView
import pe.logyx.core.model.SubmitReviewRequest
import pe.logyx.core.network.LogyxApi

/** Documentos del envío (ver / descargar). La subida de evidencia del conductor va por [EvidenceRepository]. */
interface DocumentsRepository {
    suspend fun list(shipmentId: String): Result<List<DocumentView>>
    suspend fun file(documentId: String): Result<ByteArray>
    /** Sube un archivo suelto (p. ej. foto de una incidencia como `OTHER`); las JPEG se comprimen a <= 2 MB. */
    suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: pe.logyx.core.model.DocumentType, stopId: String? = null): Result<DocumentView>
}

/** Incidencias (RF52-55, RN11). */
interface IncidentsRepository {
    suspend fun report(shipmentId: String, category: IncidentCategory, description: String): Result<IncidentView>
    suspend fun forShipment(shipmentId: String): Result<List<IncidentView>>
    suspend fun detail(id: String): Result<IncidentView>
    suspend fun comment(id: String, body: String): Result<IncidentView>
    suspend fun mine(active: Boolean? = null, page: Int = 0): Result<Page<IncidentView>>
    /**
     * Reporte completo (V23, 2.7): severidad, ubicación, hora del dispositivo, adjuntos. Con [idempotencyKey] un reintento
     * devuelve la ya creada. Por omisión delega en el reporte simple (fakes viejos).
     */
    suspend fun report(shipmentId: String, request: ReportIncidentRequest, idempotencyKey: String?): Result<IncidentView> =
        report(shipmentId, request.category, request.description)
    /** Quien la reportó retira una incidencia informativa activa (`canWithdraw`). */
    suspend fun withdraw(id: String): Result<IncidentView> = Result.failure(UnsupportedOperationException())
}

/** Chat por solicitud y contra-ofertas (RF25-28, RN07). */
interface NegotiationRepository {
    suspend fun threads(requestId: String): Result<List<ChatThreadView>>
    suspend fun messages(requestId: String, carrierOrgId: String, before: String? = null): Result<CursorPage<MessageView>>
    suspend fun send(requestId: String, carrierOrgId: String, body: String): Result<MessageView>
    suspend fun counterOffers(bidId: String): Result<List<CounterOfferView>>
    suspend fun propose(bidId: String, amount: Double, message: String?): Result<CounterOfferView>
    suspend fun accept(counterOfferId: String): Result<CounterOfferView>
    suspend fun reject(counterOfferId: String): Result<CounterOfferView>
}

/** Reputación y calificaciones (RN06, RN09). */
interface ReputationRepository {
    suspend fun reputation(organizationId: String): Result<ReputationView>
    suspend fun reviews(organizationId: String, page: Int = 0): Result<Page<ReviewView>>
    suspend fun shipmentReviews(shipmentId: String): Result<ShipmentReviewsView>
    suspend fun submit(shipmentId: String, punctuality: Int, cargoCondition: Int, communication: Int, comment: String?): Result<ReviewView>
}

/** Centro de notificaciones in-app (RF49, RF51). */
interface NotificationsRepository {
    /** V16: avisos por persona; [mineOnly] = solo los de algo que creé. */
    suspend fun list(unreadOnly: Boolean = false, page: Int = 0, mineOnly: Boolean = false): Result<Page<NotificationView>>
    suspend fun unreadCount(): Result<Int>
    suspend fun markRead(id: String): Result<NotificationView>
    suspend fun markAllRead(): Result<Int>
    /** Preferencias de aviso (V16). Default para no romper fakes viejos. */
    suspend fun preferences(): Result<List<pe.logyx.core.model.NotificationPreferenceView>> =
        Result.failure(UnsupportedOperationException())
    suspend fun updatePreferences(changes: List<pe.logyx.core.model.NotificationPreferenceChange>): Result<List<pe.logyx.core.model.NotificationPreferenceView>> =
        Result.failure(UnsupportedOperationException())
}

class DefaultDocumentsRepository(private val api: LogyxApi) : DocumentsRepository {
    override suspend fun list(shipmentId: String) = apiCall { api.shipmentDocuments(shipmentId) }
    override suspend fun file(documentId: String) = apiCall { api.documentFile(documentId) }
    override suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: pe.logyx.core.model.DocumentType, stopId: String?) =
        apiCall {
            val data = if (mime == "image/jpeg") kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ImageCompressor.compressJpeg(bytes) } else bytes
            api.uploadDocument(shipmentId, data, fileName, mime, type, stopId)
        }
}

class DefaultIncidentsRepository(private val api: LogyxApi) : IncidentsRepository {
    override suspend fun report(shipmentId: String, category: IncidentCategory, description: String) =
        apiCall { api.reportIncident(shipmentId, ReportIncidentRequest(category, description.trim())) }
    override suspend fun forShipment(shipmentId: String) = apiCall { api.shipmentIncidents(shipmentId) }
    override suspend fun detail(id: String) = apiCall { api.incident(id) }
    override suspend fun comment(id: String, body: String) = apiCall { api.commentIncident(id, body.trim()) }
    override suspend fun mine(active: Boolean?, page: Int) = apiCall { api.incidents(active, page) }
    override suspend fun report(shipmentId: String, request: ReportIncidentRequest, idempotencyKey: String?) =
        apiCall { api.reportIncident(shipmentId, request.copy(description = request.description.trim()), idempotencyKey) }
    override suspend fun withdraw(id: String) = apiCall { api.withdrawIncident(id) }
}

class DefaultNegotiationRepository(private val api: LogyxApi) : NegotiationRepository {
    override suspend fun threads(requestId: String) = apiCall { api.threads(requestId) }
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?) =
        apiCall { api.messages(requestId, carrierOrgId, before) }
    override suspend fun send(requestId: String, carrierOrgId: String, body: String) =
        apiCall { api.postMessage(requestId, carrierOrgId, body.trim()) }
    override suspend fun counterOffers(bidId: String) = apiCall { api.counterOffers(bidId) }
    override suspend fun propose(bidId: String, amount: Double, message: String?) = apiCall { api.proposeCounterOffer(bidId, amount, message) }
    override suspend fun accept(counterOfferId: String) = apiCall { api.acceptCounterOffer(counterOfferId) }
    override suspend fun reject(counterOfferId: String) = apiCall { api.rejectCounterOffer(counterOfferId) }
}

class DefaultReputationRepository(private val api: LogyxApi) : ReputationRepository {
    override suspend fun reputation(organizationId: String) = apiCall { api.reputation(organizationId) }
    override suspend fun reviews(organizationId: String, page: Int) = apiCall { api.organizationReviews(organizationId, page) }
    override suspend fun shipmentReviews(shipmentId: String) = apiCall { api.shipmentReviews(shipmentId) }
    override suspend fun submit(shipmentId: String, punctuality: Int, cargoCondition: Int, communication: Int, comment: String?) =
        apiCall { api.submitReview(shipmentId, SubmitReviewRequest(punctuality, cargoCondition, communication, comment?.trim()?.takeIf { it.isNotEmpty() })) }
}

class DefaultNotificationsRepository(private val api: LogyxApi) : NotificationsRepository {
    override suspend fun list(unreadOnly: Boolean, page: Int, mineOnly: Boolean) = apiCall { api.notifications(unreadOnly, page, mineOnly = mineOnly) }
    override suspend fun unreadCount() = apiCall { api.unreadCount().unread }
    override suspend fun markRead(id: String) = apiCall { api.markNotificationRead(id) }
    override suspend fun markAllRead() = apiCall { api.markAllNotificationsRead().updated }
    override suspend fun preferences() = apiCall { api.notificationPreferences() }
    override suspend fun updatePreferences(changes: List<pe.logyx.core.model.NotificationPreferenceChange>) =
        apiCall { api.updateNotificationPreferences(changes) }
}

/** «Pedir acceso» a los ADMIN de la organización (V16, roadmap v2 1.7/1.15). Cualquier rol, también Lector. */
interface AccessRepository {
    suspend fun request(permission: pe.logyx.core.model.OrgPermission, reason: String? = null): Result<pe.logyx.core.model.AccessRequestView>
}

class DefaultAccessRepository(private val api: LogyxApi) : AccessRepository {
    override suspend fun request(permission: pe.logyx.core.model.OrgPermission, reason: String?) =
        apiCall { api.requestAccess(pe.logyx.core.model.CreateAccessRequest(permission, reason?.takeIf { it.isNotBlank() })) }
}
