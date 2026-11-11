package pe.logyx.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.model.AuctionStatus
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
}

@Composable
fun AuctionStatus.label() = when (this) {
    AuctionStatus.OPEN -> stringResource(R.string.ds_auctionstatus_open)
    AuctionStatus.CLOSED -> stringResource(R.string.ds_auctionstatus_closed)
    AuctionStatus.CANCELLED -> stringResource(R.string.ds_auctionstatus_cancelled)
}

@Composable
fun BidStatus.pill() = when (this) {
    BidStatus.PENDING -> stringResource(R.string.ds_bidstatus_pending) to PillTone.Amber
    BidStatus.ACCEPTED -> stringResource(R.string.ds_bidstatus_accepted) to PillTone.Emerald
    BidStatus.REJECTED -> stringResource(R.string.ds_bidstatus_rejected) to PillTone.Red
}

@Composable
fun ShipmentStatus.pill() = when (this) {
    ShipmentStatus.PENDING -> stringResource(R.string.ds_shipmentstatus_pending) to PillTone.Amber
    ShipmentStatus.IN_TRANSIT -> stringResource(R.string.ds_shipmentstatus_in_transit) to PillTone.Petrol
    ShipmentStatus.DELIVERED -> stringResource(R.string.ds_shipmentstatus_delivered) to PillTone.Emerald
    ShipmentStatus.CANCELLED -> stringResource(R.string.ds_shipmentstatus_cancelled) to PillTone.Neutral
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
}
