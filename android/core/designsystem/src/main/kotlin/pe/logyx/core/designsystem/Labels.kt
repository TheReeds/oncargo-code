package pe.logyx.core.designsystem

import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.VehicleType

/** Textos en español para enums del contrato. */
val CargoType.label: String
    get() = when (this) {
        CargoType.GENERAL -> "Carga general"
        CargoType.FRAGILE -> "Frágil"
        CargoType.PERISHABLE -> "Perecible"
        CargoType.HAZARDOUS -> "Peligrosa"
        CargoType.BULK -> "Granel"
        CargoType.OVERSIZED -> "Sobredimensionada"
    }

val VehicleType.label: String
    get() = when (this) {
        VehicleType.VAN -> "Van"
        VehicleType.TRUCK_SMALL -> "Camión pequeño"
        VehicleType.TRUCK_MEDIUM -> "Camión mediano"
        VehicleType.TRUCK_LARGE -> "Camión grande"
        VehicleType.PLATFORM -> "Plataforma"
        VehicleType.REFRIGERATED -> "Refrigerado"
        VehicleType.TANKER -> "Cisterna"
    }

val StopType.label: String
    get() = when (this) {
        StopType.PICKUP -> "Recojo"
        StopType.DELIVERY -> "Entrega"
    }

val StopStatus.label: String
    get() = when (this) {
        StopStatus.PENDING -> "Pendiente"
        StopStatus.ARRIVED -> "En el punto"
        StopStatus.COMPLETED -> "Completada"
    }

/** Nombre de ciudad a partir del código (`AREQUIPA` -> `Arequipa`) cuando aún no se cargó el catálogo. */
fun cityLabel(code: String?): String = code.orEmpty().lowercase().replaceFirstChar { it.uppercase() }

fun RequestStatus.pill() = when (this) {
    RequestStatus.PUBLISHED -> "Publicada" to PillTone.Petrol
    RequestStatus.MATCHED -> "Adjudicada" to PillTone.Emerald
    RequestStatus.CANCELLED -> "Cancelada" to PillTone.Neutral
}

fun AuctionStatus.label() = when (this) {
    AuctionStatus.OPEN -> "Subasta abierta"
    AuctionStatus.CLOSED -> "Subasta cerrada"
    AuctionStatus.CANCELLED -> "Subasta cancelada"
}

fun BidStatus.pill() = when (this) {
    BidStatus.PENDING -> "Pendiente" to PillTone.Amber
    BidStatus.ACCEPTED -> "Ganada" to PillTone.Emerald
    BidStatus.REJECTED -> "No elegida" to PillTone.Red
}

fun ShipmentStatus.pill() = when (this) {
    ShipmentStatus.PENDING -> "Por recoger" to PillTone.Amber
    ShipmentStatus.IN_TRANSIT -> "En tránsito" to PillTone.Petrol
    ShipmentStatus.DELIVERED -> "Entregado" to PillTone.Emerald
    ShipmentStatus.CANCELLED -> "Cancelado" to PillTone.Neutral
}
