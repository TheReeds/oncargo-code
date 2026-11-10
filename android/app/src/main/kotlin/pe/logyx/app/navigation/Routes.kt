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

// Destinos secundarios
@Serializable data class LoadDetail(val id: String)
@Serializable data class RequestDetail(val id: String)
@Serializable data class ShipmentDetail(val id: String)
