package pe.logyx.core.model

/**
 * Matriz RN02 (espejo de `fleet/domain/VehicleCargoCompatibility` del backend, ver `docs/domain/business-rules.md`):
 * tipo de carga → tipos de vehículo aptos. El refrigerado se reserva a perecederos; la cisterna lleva carga a granel y peligrosa.
 */
val CargoType.compatibleVehicleTypes: List<VehicleType>
    get() = when (this) {
        CargoType.GENERAL -> listOf(VehicleType.VAN, VehicleType.TRUCK_SMALL, VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE, VehicleType.PLATFORM)
        CargoType.FRAGILE -> listOf(VehicleType.VAN, VehicleType.TRUCK_SMALL, VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE)
        CargoType.PERISHABLE -> listOf(VehicleType.REFRIGERATED)
        CargoType.HAZARDOUS -> listOf(VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE, VehicleType.TANKER)
        CargoType.BULK -> listOf(VehicleType.TRUCK_SMALL, VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE, VehicleType.PLATFORM, VehicleType.TANKER)
        CargoType.OVERSIZED -> listOf(VehicleType.PLATFORM)
    }

/**
 * Tipos de vehículo aptos para la carga: lo que dice el servidor (`RequestView.compatibleVehicleTypes`) y, si esa lista llega
 * vacía (API anterior a V13), la matriz RN02 local.
 */
val RequestView.suitableVehicleTypes: List<VehicleType>
    get() = compatibleVehicleTypes.ifEmpty { cargoType.compatibleVehicleTypes }

/** Por qué un vehículo puede o no ofertar por una carga. Espejo de `CAP-001..004` del backend (`ensureSuitable`). */
enum class VehicleFit(val code: String?) {
    /** Apto. */
    FITS(null),

    /** `LOGYX-CAP-002`: no está disponible (en ruta o en mantenimiento). */
    UNAVAILABLE("LOGYX-CAP-002"),

    /** `LOGYX-CAP-001`: su capacidad en kg no alcanza. */
    WEIGHT("LOGYX-CAP-001"),

    /** `LOGYX-CAP-003`: el tipo de vehículo no sirve para este tipo de carga (matriz RN02). */
    TYPE("LOGYX-CAP-003"),

    /** `LOGYX-CAP-004`: el volumen de la carga no cabe. */
    VOLUME("LOGYX-CAP-004"),
    ;

    val isFit: Boolean get() = this == FITS
}

/**
 * Única función de compatibilidad vehículo ↔ carga (RN02): la usan el selector de la hoja de oferta, el cambio de vehículo del
 * envío y el chip "Compatible" del feed. Se evalúa en el mismo orden que el backend: disponibilidad, kg, tipo y m³.
 */
fun VehicleView.fitFor(request: RequestView): VehicleFit = fitFor(request.weightKg, request.volumeM3, request.suitableVehicleTypes)

/** Variante con los datos sueltos (p. ej. un envío, que trae el peso pero no el volumen: pasa `volumeM3 = null`). */
fun VehicleView.fitFor(weightKg: Double, volumeM3: Double?, suitableTypes: List<VehicleType>): VehicleFit = when {
    status != VehicleStatus.AVAILABLE -> VehicleFit.UNAVAILABLE
    maxWeightKg < weightKg -> VehicleFit.WEIGHT
    type !in suitableTypes -> VehicleFit.TYPE
    volumeM3 != null && maxVolumeM3 != null && maxVolumeM3 < volumeM3 -> VehicleFit.VOLUME
    else -> VehicleFit.FITS
}

/** Un vehículo puede llevar la carga si está disponible **ahora** y es apto en kg, tipo y volumen (cambio de vehículo del envío). */
fun VehicleView.canCarry(request: RequestView): Boolean = fitFor(request).isFit

/**
 * RN02 por fecha de recojo (decisión del dueño, 2026-11-17): para **ofertar** no importa que el vehículo esté en ruta hoy;
 * solo el mantenimiento bloquea (`CAP-002`). Que esté libre esa fecha lo dice la agenda
 * (`GET /shipment-requests/{id}/vehicle-eligibility`) y lo exige el servidor.
 */
fun VehicleView.fitForBid(request: RequestView): VehicleFit =
    if (status == VehicleStatus.MAINTENANCE) VehicleFit.UNAVAILABLE
    else copy(status = VehicleStatus.AVAILABLE).fitFor(request)

/** Puede ofertar a esa carga (tipo, kg, m³ y no está en mantenimiento). */
fun VehicleView.canBid(request: RequestView): Boolean = fitForBid(request).isFit

/** Alguno de los vehículos de la flota puede ofertar (chip "Compatible" del feed, RF16/F35). */
fun RequestView.isCompatibleWith(fleet: List<VehicleView>): Boolean = fleet.any { it.canBid(this) }

/** Normaliza ciudades para comparar códigos (`AREQUIPA`) con nombres libres (`Arequipa`, `Lima `). */
fun normalizeCity(value: String?): String =
    java.text.Normalizer.normalize(value.orEmpty().trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase()

fun ShipmentView.nextStop(): StopView? =
    stops.sortedBy { it.sequence }.firstOrNull { it.status != StopStatus.COMPLETED }

val ShipmentView.isActive: Boolean
    get() = status == ShipmentStatus.PENDING || status == ShipmentStatus.IN_TRANSIT

/** Falta un conductor (envío vivo sin asignar): la lista del transportista lo marca "Sin conductor" (F36). */
val ShipmentView.needsDriver: Boolean
    get() = status == ShipmentStatus.PENDING && driverId == null

/** Las partes aún pueden cancelar el envío: solo antes del recojo (`SHP-003`) y sin incidencia activa (`TRK-005`). */
val ShipmentView.isCancellable: Boolean
    get() = status == ShipmentStatus.PENDING && !hasActiveIncident

/** El transportista puede cambiar el vehículo o relevar al conductor (pendiente o en ruta). */
val ShipmentView.isReassignable: Boolean
    get() = isActive

/**
 * Resultado de mi oferta visto desde "Mis ofertas" (F15/F51). La API ahora devuelve también las solicitudes que ya se
 * adjudicaron a otro o se cancelaron, con mi oferta `REJECTED`/`WITHDRAWN` y el estado de la solicitud.
 */
enum class BidOutcome {
    /** Pendiente con la subasta abierta: se puede contraofertar o retirar. */
    OPEN,

    /** Pendiente, pero la subasta ya cerró: la PYME aún debe decidir. */
    AWAITING_DECISION,

    /** La PYME me adjudicó la carga. */
    WON,

    /** Se adjudicó a otro transportista. */
    LOST,

    /** La PYME canceló la solicitud. */
    REQUEST_CANCELLED,

    /** Yo retiré la oferta. */
    WITHDRAWN,
    UNKNOWN,
}

val RequestView.myBidOutcome: BidOutcome?
    get() {
        val bid = myBid ?: return null
        return when (bid.status) {
            BidStatus.WITHDRAWN -> BidOutcome.WITHDRAWN
            BidStatus.ACCEPTED -> BidOutcome.WON
            BidStatus.REJECTED -> if (status == RequestStatus.CANCELLED) BidOutcome.REQUEST_CANCELLED else BidOutcome.LOST
            BidStatus.PENDING -> when {
                status == RequestStatus.CANCELLED -> BidOutcome.REQUEST_CANCELLED
                status == RequestStatus.MATCHED -> BidOutcome.LOST
                auction?.status == AuctionStatus.OPEN -> BidOutcome.OPEN
                else -> BidOutcome.AWAITING_DECISION
            }
            BidStatus.UNKNOWN -> BidOutcome.UNKNOWN
        }
    }

/** Mi oferta admite contraoferta o retiro: pendiente, solicitud publicada y subasta aceptando ofertas (`BID-005`). */
val RequestView.canManageMyBid: Boolean get() = myBidOutcome == BidOutcome.OPEN

/** La PYME puede reabrir la subasta: cerró por tiempo sin adjudicar (`AUC-003`). */
val RequestView.canReopenAuction: Boolean
    get() = status == RequestStatus.PUBLISHED && auction?.status == AuctionStatus.CLOSED && auction?.closeReason == CloseReason.TIMEOUT

/** La PYME puede republicar: solo una solicitud cancelada (`REQ-004`). */
val RequestView.canRepublish: Boolean get() = status == RequestStatus.CANCELLED

/** La PYME aún puede cancelar la solicitud (no adjudicada ni cancelada). */
val RequestView.canCancelRequest: Boolean get() = status == RequestStatus.PUBLISHED

/** Contraoferta vigente y propuesta por la contraparte de quien mira: la que se puede aceptar o rechazar. */
fun CounterOfferView.isPendingFor(viewerIsCarrier: Boolean): Boolean =
    status == CounterOfferStatus.PENDING && proposedByCarrier != viewerIsCarrier
