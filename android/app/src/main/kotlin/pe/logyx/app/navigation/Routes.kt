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

// Destinos secundarios
@Serializable data class LoadDetail(val id: String)
@Serializable data class RequestDetail(val id: String)
@Serializable data class ShipmentDetail(val id: String)
@Serializable data class Chat(val requestId: String, val carrierOrgId: String, val title: String? = null)
@Serializable data class Threads(val requestId: String)
@Serializable data class OrgProfile(val orgId: String)
@Serializable data class Review(val shipmentId: String)
@Serializable data class Documents(val shipmentId: String)
@Serializable data class IncidentsList(val shipmentId: String)
@Serializable data class ReportIncident(val shipmentId: String)
@Serializable data class IncidentDetail(val incidentId: String)
