package pe.logyx.core.model

/** A dónde lleva tocar una notificación. `:app` lo traduce a una ruta de navegación según el rol. */
sealed interface NotificationDestination {
    /** Detalle de una solicitud (PYME: comparación de ofertas; transportista: detalle de la carga). */
    data class Request(val id: String) : NotificationDestination
    /** Conversación de una solicitud (PYME: hilos; transportista: su chat). */
    data class Conversation(val requestId: String) : NotificationDestination
    data class Shipment(val id: String) : NotificationDestination
    data class Incident(val id: String) : NotificationDestination
    data class Trip(val id: String) : NotificationDestination
    data object SmartLoads : NotificationDestination
    /** Pestaña de ofertas (transportista) o de solicitudes (PYME). */
    data object BidsOrRequests : NotificationDestination
    /** Perfil público de la propia organización (reseñas). */
    data object OwnProfile : NotificationDestination
    /** Cuenta: estado de verificación de la organización (RN01). */
    data object Account : NotificationDestination
    /** Plantillas de la PYME (aviso `TEMPLATE_RUN`). */
    data object Templates : NotificationDestination
    /**
     * Segunda oferta (`BACKUP_REQUESTED`, 2.9): el transportista confirma en la web (`/transportista/confirmaciones`);
     * el móvil abre esa página en el navegador.
     */
    data object BackupConfirmations : NotificationDestination
}

/**
 * Destino de una notificación según `relatedType`/`relatedId`. Los tipos nuevos de V13 (`AUCTION_CLOSED`, `REQUEST_CANCELLED`,
 * `SHIPMENT_CANCELLED`, `BID_WITHDRAWN`) llegan con el recurso relacionado, así que caen en las reglas generales;
 * `ORGANIZATION_VERIFICATION` (`relatedType = ORGANIZATION`) lleva a Cuenta. Ola 2: `ADJUSTMENT_*` llegan con
 * `SHIPMENT` (abren el envío, tarjeta «Precio y ajustes»), `REQUEST_INVITATION`/`BACKUP_RESPONDED` con `REQUEST`,
 * `INCIDENT_ASSIGNED` con `INCIDENT`; `TEMPLATE_RUN` trae `REQUEST` (publicada) o `TEMPLATE` (falló → Plantillas) y
 * `BACKUP_REQUESTED` abre las confirmaciones de la web. Devuelve null si no hay a dónde ir.
 */
fun NotificationView.destination(): NotificationDestination? {
    if (relatedType == NotificationTarget.ORGANIZATION || type == NotificationType.ORGANIZATION_VERIFICATION) {
        return NotificationDestination.Account
    }
    if (type == NotificationType.BACKUP_REQUESTED) return NotificationDestination.BackupConfirmations
    val id = relatedId
    return when {
        relatedType == NotificationTarget.TEMPLATE || type == NotificationType.TEMPLATE_RUN && relatedType == null ->
            NotificationDestination.Templates
        relatedType == NotificationTarget.SMART_LOAD -> NotificationDestination.SmartLoads
        id == null -> null
        relatedType == NotificationTarget.TRIP -> NotificationDestination.Trip(id)
        type == NotificationType.NEW_MESSAGE && relatedType == NotificationTarget.REQUEST -> NotificationDestination.Conversation(id)
        relatedType == NotificationTarget.REQUEST -> NotificationDestination.Request(id)
        relatedType == NotificationTarget.SHIPMENT -> NotificationDestination.Shipment(id)
        relatedType == NotificationTarget.INCIDENT -> NotificationDestination.Incident(id)
        relatedType == NotificationTarget.COUNTER_OFFER || relatedType == NotificationTarget.BID -> NotificationDestination.BidsOrRequests
        relatedType == NotificationTarget.REVIEW -> NotificationDestination.OwnProfile
        else -> null
    }
}
