package pe.logyx.core.model

import kotlinx.serialization.Serializable

// Cuerpos de petición de V13 (huecos P1). Los campos de texto libre están sujetos a RN07 (`LOGYX-NEG-001`, `field` = nombre).

/** `POST /shipment-requests/{id}/cancel` (cuerpo opcional, máx. 500): se avisa a los transportistas que ofertaron. */
@Serializable
data class CancelRequestBody(val reason: String? = null)

/** `POST /shipments/{id}/cancel`: motivo obligatorio (máx. 500). */
@Serializable
data class CancelShipmentRequest(val reason: String)

/** `PUT /shipments/{id}/vehicle`: `reason` obligatorio con el envío en ruta (`LOGYX-SHP-006`). */
@Serializable
data class ChangeVehicleRequest(val vehicleId: String, val reason: String? = null)

/** `POST /shipment-requests/{id}/reopen` y `/republish`: fecha de recojo opcional (`yyyy-MM-dd`). */
@Serializable
data class RescheduleRequest(val pickupDate: String? = null)

/** `GET /organizations/me` (V14). `legalDataEditable`: razón social y RUC solo se editan con PENDING/REJECTED. */
@Serializable
data class OrganizationProfile(
    val id: String,
    val type: OrgType,
    val legalName: String,
    val ruc: String? = null,
    val rucValid: Boolean = false,
    val rucVerified: Boolean = false,
    val verificationStatus: VerificationStatus = VerificationStatus.UNKNOWN,
    val verificationNote: String? = null,
    val city: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val legalDataEditable: Boolean = false,
    val createdAt: String? = null,
)
