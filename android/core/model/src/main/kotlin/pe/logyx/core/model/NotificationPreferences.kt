package pe.logyx.core.model

import kotlinx.serialization.Serializable

/**
 * Preferencias de aviso por persona (V16, `GET/PUT /notifications/preferences`): una fila por categoría que aplica a tu
 * tipo de cuenta, con los canales in-app y correo. `ACCOUNT` («Cuenta y accesos») no se puede apagar in-app
 * (`inAppLocked`, `LOGYX-NTF-001`). Categorías nuevas del servidor caen en `UNKNOWN` (`coerceInputValues`) y no se editan.
 */
@Serializable
enum class NotificationCategory { BIDS, AWARDS, REQUESTS, NEGOTIATION, SHIPMENTS, INCIDENTS, REVIEWS, TRIPS, SMART_LOAD, ALERTS, FLEET, ACCOUNT, UNKNOWN }

@Serializable
data class NotificationPreferenceView(
    val category: NotificationCategory = NotificationCategory.UNKNOWN,
    val label: String = "",
    val description: String? = null,
    val inApp: Boolean = true,
    val email: Boolean = false,
    val inAppLocked: Boolean = false,
)

@Serializable
data class NotificationPreferenceChange(val category: NotificationCategory, val inApp: Boolean, val email: Boolean)

/** Cuerpo del `PUT`: solo cambian las categorías enviadas (1–20). */
@Serializable
data class UpdateNotificationPreferences(val preferences: List<NotificationPreferenceChange>)

/** Lo que se envía al cambiar un canal de una fila (sin `UNKNOWN`). */
fun NotificationPreferenceView.toChange(): NotificationPreferenceChange? =
    if (category == NotificationCategory.UNKNOWN) null else NotificationPreferenceChange(category, inApp || inAppLocked, email)
