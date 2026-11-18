package pe.logyx.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.AuctionView
import pe.logyx.core.model.BidOutcome
import pe.logyx.core.model.BidStandingBand
import pe.logyx.core.model.CloseReason
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.SmartLoadStatus
import pe.logyx.core.model.TripPricingMode
import pe.logyx.core.model.TripStatus
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentStatus
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.VehicleType

/** Textos en español para enums del contrato. */
val CargoType.label: String
    @Composable get() = when (this) {
        CargoType.GENERAL -> stringResource(R.string.ds_cargotype_general)
        CargoType.FRAGILE -> stringResource(R.string.ds_cargotype_fragile)
        CargoType.PERISHABLE -> stringResource(R.string.ds_cargotype_perishable)
        CargoType.HAZARDOUS -> stringResource(R.string.ds_cargotype_hazardous)
        CargoType.BULK -> stringResource(R.string.ds_cargotype_bulk)
        CargoType.OVERSIZED -> stringResource(R.string.ds_cargotype_oversized)
    }

val VehicleType.label: String
    @Composable get() = when (this) {
        VehicleType.VAN -> stringResource(R.string.ds_vehicletype_van)
        VehicleType.TRUCK_SMALL -> stringResource(R.string.ds_vehicletype_truck_small)
        VehicleType.TRUCK_MEDIUM -> stringResource(R.string.ds_vehicletype_truck_medium)
        VehicleType.TRUCK_LARGE -> stringResource(R.string.ds_vehicletype_truck_large)
        VehicleType.PLATFORM -> stringResource(R.string.ds_vehicletype_platform)
        VehicleType.REFRIGERATED -> stringResource(R.string.ds_vehicletype_refrigerated)
        VehicleType.TANKER -> stringResource(R.string.ds_vehicletype_tanker)
    }

val StopType.label: String
    @Composable get() = when (this) {
        StopType.PICKUP -> stringResource(R.string.ds_stoptype_pickup)
        StopType.DELIVERY -> stringResource(R.string.ds_stoptype_delivery)
    }

val StopStatus.label: String
    @Composable get() = when (this) {
        StopStatus.PENDING -> stringResource(R.string.ds_stopstatus_pending)
        StopStatus.ARRIVED -> stringResource(R.string.ds_stopstatus_arrived)
        StopStatus.COMPLETED -> stringResource(R.string.ds_stopstatus_completed)
    }

/** Nombre de ciudad a partir del código (`AREQUIPA` -> `Arequipa`) cuando aún no se cargó el catálogo. */
fun cityLabel(code: String?): String = code.orEmpty().lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun RequestStatus.pill() = when (this) {
    RequestStatus.PUBLISHED -> stringResource(R.string.ds_requeststatus_published) to PillTone.Petrol
    RequestStatus.MATCHED -> stringResource(R.string.ds_requeststatus_matched) to PillTone.Emerald
    RequestStatus.CANCELLED -> stringResource(R.string.ds_requeststatus_cancelled) to PillTone.Neutral
    RequestStatus.UNKNOWN -> stringResource(R.string.ds_status_unknown) to PillTone.Neutral
}

@Composable
fun AuctionStatus.label() = when (this) {
    AuctionStatus.OPEN -> stringResource(R.string.ds_auctionstatus_open)
    AuctionStatus.CLOSED -> stringResource(R.string.ds_auctionstatus_closed)
    AuctionStatus.CANCELLED -> stringResource(R.string.ds_auctionstatus_cancelled)
    AuctionStatus.UNKNOWN -> stringResource(R.string.ds_status_unknown)
}

@Composable
fun BidStatus.pill() = when (this) {
    BidStatus.PENDING -> stringResource(R.string.ds_bidstatus_pending) to PillTone.Amber
    BidStatus.ACCEPTED -> stringResource(R.string.ds_bidstatus_accepted) to PillTone.Emerald
    BidStatus.REJECTED -> stringResource(R.string.ds_bidstatus_rejected) to PillTone.Red
    BidStatus.WITHDRAWN -> stringResource(R.string.ds_bidstatus_withdrawn) to PillTone.Neutral
    BidStatus.UNKNOWN -> stringResource(R.string.ds_status_unknown) to PillTone.Neutral
}

@Composable
fun ShipmentStatus.pill() = when (this) {
    ShipmentStatus.PENDING -> stringResource(R.string.ds_shipmentstatus_pending) to PillTone.Amber
    ShipmentStatus.IN_TRANSIT -> stringResource(R.string.ds_shipmentstatus_in_transit) to PillTone.Petrol
    ShipmentStatus.DELIVERED -> stringResource(R.string.ds_shipmentstatus_delivered) to PillTone.Emerald
    ShipmentStatus.CANCELLED -> stringResource(R.string.ds_shipmentstatus_cancelled) to PillTone.Neutral
    ShipmentStatus.UNKNOWN -> stringResource(R.string.ds_status_unknown) to PillTone.Neutral
}

val IncidentCategory.label: String
    @Composable get() = stringResource(
        when (this) {
            IncidentCategory.CARGO_DAMAGE -> R.string.ds_incidentcategory_cargo_damage
            IncidentCategory.DELAY -> R.string.ds_incidentcategory_delay
            IncidentCategory.LOST_CARGO -> R.string.ds_incidentcategory_lost_cargo
            IncidentCategory.WRONG_DELIVERY -> R.string.ds_incidentcategory_wrong_delivery
            IncidentCategory.DRIVER_CONDUCT -> R.string.ds_incidentcategory_driver_conduct
            IncidentCategory.OTHER -> R.string.ds_incidentcategory_other
        },
    )

@Composable
fun IncidentStatus.pill() = when (this) {
    IncidentStatus.OPEN -> stringResource(R.string.ds_incidentstatus_open) to PillTone.Red
    IncidentStatus.IN_REVIEW -> stringResource(R.string.ds_incidentstatus_in_review) to PillTone.Amber
    IncidentStatus.RESOLVED -> stringResource(R.string.ds_incidentstatus_resolved) to PillTone.Emerald
    IncidentStatus.CLOSED -> stringResource(R.string.ds_incidentstatus_closed) to PillTone.Neutral
}

val DocumentType.label: String
    @Composable get() = stringResource(
        when (this) {
            DocumentType.SERVICE_ORDER -> R.string.ds_documenttype_service_order
            DocumentType.REMISSION_GUIDE -> R.string.ds_documenttype_remission_guide
            DocumentType.RECEIPT -> R.string.ds_documenttype_receipt
            DocumentType.DELIVERY_PHOTO -> R.string.ds_documenttype_delivery_photo
            DocumentType.SIGNATURE -> R.string.ds_documenttype_signature
            DocumentType.OTHER -> R.string.ds_documenttype_other
        },
    )

@Composable
fun CounterOfferStatus.pill() = when (this) {
    CounterOfferStatus.PENDING -> stringResource(R.string.ds_counteroffer_pending) to PillTone.Amber
    CounterOfferStatus.ACCEPTED -> stringResource(R.string.ds_counteroffer_accepted) to PillTone.Emerald
    CounterOfferStatus.REJECTED -> stringResource(R.string.ds_counteroffer_rejected) to PillTone.Red
    CounterOfferStatus.EXPIRED -> stringResource(R.string.ds_counteroffer_expired) to PillTone.Neutral
    CounterOfferStatus.UNKNOWN -> stringResource(R.string.ds_status_unknown) to PillTone.Neutral
}

// --- Fase 4: viajes, reservas y Smart Load ---
@Composable
fun TripStatus.pill() = when (this) {
    TripStatus.OPEN -> stringResource(R.string.ds_tripstatus_open) to PillTone.Emerald
    TripStatus.FILLING -> stringResource(R.string.ds_tripstatus_filling) to PillTone.Petrol
    TripStatus.CLOSED -> stringResource(R.string.ds_tripstatus_closed) to PillTone.Neutral
    TripStatus.COMPLETED -> stringResource(R.string.ds_tripstatus_completed) to PillTone.Emerald
    TripStatus.CANCELLED -> stringResource(R.string.ds_tripstatus_cancelled) to PillTone.Neutral
}

@Composable
fun BookingStatus.pill() = when (this) {
    BookingStatus.PENDING -> stringResource(R.string.ds_bookingstatus_pending) to PillTone.Amber
    BookingStatus.CONFIRMED -> stringResource(R.string.ds_bookingstatus_confirmed) to PillTone.Emerald
    BookingStatus.REJECTED -> stringResource(R.string.ds_bookingstatus_rejected) to PillTone.Red
    BookingStatus.CANCELLED -> stringResource(R.string.ds_bookingstatus_cancelled) to PillTone.Neutral
}

val TripPricingMode.label: String
    @Composable get() = stringResource(
        when (this) {
            TripPricingMode.PER_KG -> R.string.ds_pricing_per_kg
            TripPricingMode.PER_TRIP -> R.string.ds_pricing_per_trip
        },
    )

@Composable
fun SmartLoadStatus.pill() = when (this) {
    SmartLoadStatus.PENDING -> stringResource(R.string.ds_smartload_pending) to PillTone.Amber
    SmartLoadStatus.ACCEPTED -> stringResource(R.string.ds_smartload_accepted) to PillTone.Emerald
    SmartLoadStatus.REJECTED -> stringResource(R.string.ds_smartload_rejected) to PillTone.Red
    SmartLoadStatus.EXPIRED -> stringResource(R.string.ds_smartload_expired) to PillTone.Neutral
}

// --- V13-V15: resultado de mi oferta, posición y cierre de la subasta ---
/** Estado de mi oferta en "Mis ofertas" (F15: Perdida, Cancelada y Retirada también se listan). */
@Composable
fun BidOutcome.pill() = when (this) {
    BidOutcome.OPEN -> stringResource(R.string.ds_bidoutcome_open) to PillTone.Amber
    BidOutcome.AWAITING_DECISION -> stringResource(R.string.ds_bidoutcome_awaiting) to PillTone.Petrol
    BidOutcome.WON -> stringResource(R.string.ds_bidoutcome_won) to PillTone.Emerald
    BidOutcome.LOST -> stringResource(R.string.ds_bidoutcome_lost) to PillTone.Red
    BidOutcome.REQUEST_CANCELLED -> stringResource(R.string.ds_bidoutcome_cancelled) to PillTone.Neutral
    BidOutcome.WITHDRAWN -> stringResource(R.string.ds_bidoutcome_withdrawn) to PillTone.Neutral
    BidOutcome.UNKNOWN -> stringResource(R.string.ds_status_unknown) to PillTone.Neutral
}

/** Por qué cerró la subasta (`auction.closeReason`, F51). */
val CloseReason.label: String
    @Composable get() = stringResource(
        when (this) {
            CloseReason.TIMEOUT -> R.string.ds_closereason_timeout
            CloseReason.MAX_BIDS -> R.string.ds_closereason_max_bids
            CloseReason.ACCEPTED -> R.string.ds_closereason_accepted
            CloseReason.CANCELLED -> R.string.ds_closereason_cancelled
        },
    )

/** "Cerró el 28 nov · 08:00 · Se acabó el tiempo", o null si la subasta sigue abierta o no hay datos. */
@Composable
fun auctionClosureText(auction: AuctionView?): String? {
    if (auction == null || auction.status == AuctionStatus.OPEN) return null
    val reason = auction.closeReason?.label
    val whenText = auction.closedAt?.let { Fmt.dateTime(it) }
    return when {
        whenText != null && reason != null -> stringResource(R.string.ds_auction_closure_both, whenText, reason)
        whenText != null -> stringResource(R.string.ds_auction_closure_when, whenText)
        reason != null -> stringResource(R.string.ds_auction_closure_reason, reason)
        else -> null
    }
}

/** Texto cualitativo de mi posición frente a la mejor oferta; nunca revela montos ajenos (RN04). */
val BidStandingBand.label: String
    @Composable get() = stringResource(
        when (this) {
            BidStandingBand.BEST -> R.string.ds_standing_best
            BidStandingBand.WITHIN_5_PCT -> R.string.ds_standing_within_5
            BidStandingBand.WITHIN_15_PCT -> R.string.ds_standing_within_15
            BidStandingBand.ABOVE_15_PCT -> R.string.ds_standing_above_15
            BidStandingBand.UNKNOWN -> R.string.ds_status_unknown
        },
    )
