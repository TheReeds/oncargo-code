package pe.logyx.app.navigation

import kotlinx.serialization.Serializable

// Destinos de primer nivel (pestañas)
@Serializable data object CarrierHome
@Serializable data object CarrierFeed
@Serializable data object CarrierBids
@Serializable data object RequestsTab
@Serializable data object ShipmentsTab
@Serializable data object DriverTrip
@Serializable data object Account
@Serializable data object NotificationsTab
@Serializable data object PymeTrips

// Destinos secundarios
@Serializable data class LoadDetail(val id: String)
@Serializable data class RequestDetail(val id: String)
/** Ola 2 (2.16): [from] = solicitud que se duplica (formulario precargado); nulo = formulario vacío. */
@Serializable data class NewRequest(val from: String? = null)
@Serializable data object Templates
@Serializable data class ShipmentDetail(val id: String)
@Serializable data class Chat(val requestId: String, val carrierOrgId: String, val title: String? = null)
@Serializable data class Threads(val requestId: String)
@Serializable data class OrgProfile(val orgId: String)
@Serializable data class Review(val shipmentId: String)
@Serializable data class Documents(val shipmentId: String)
@Serializable data class IncidentsList(val shipmentId: String)
@Serializable data class ReportIncident(val shipmentId: String)
@Serializable data class IncidentDetail(val incidentId: String)

// Fase 4: viajes publicados, retornos y Smart Load
@Serializable data object CarrierTrips
/** [from] = viaje que se duplica (formulario precargado). */
@Serializable data class PublishTrip(val from: String? = null)
@Serializable data class PymeTripDetail(val id: String)
@Serializable data class CarrierTripDetail(val id: String, val published: Boolean = false)
@Serializable data object ReturnLoads
@Serializable data object SmartLoads
