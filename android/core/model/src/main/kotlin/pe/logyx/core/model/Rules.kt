package pe.logyx.core.model

/** Un vehículo puede ofertar si está disponible y soporta peso y volumen de la carga. */
fun VehicleView.canCarry(request: RequestView): Boolean =
    status == VehicleStatus.AVAILABLE &&
        maxWeightKg >= request.weightKg &&
        (request.volumeM3 == null || maxVolumeM3 == null || maxVolumeM3 >= request.volumeM3)

fun RequestView.isCompatibleWith(fleet: List<VehicleView>): Boolean = fleet.any { it.canCarry(this) }

/** Normaliza ciudades para comparar códigos (`AREQUIPA`) con nombres libres (`Arequipa`, `Lima `). */
fun normalizeCity(value: String?): String =
    java.text.Normalizer.normalize(value.orEmpty().trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase()

fun ShipmentView.nextStop(): StopView? =
    stops.sortedBy { it.sequence }.firstOrNull { it.status != StopStatus.COMPLETED }

val ShipmentView.isActive: Boolean
    get() = status == ShipmentStatus.PENDING || status == ShipmentStatus.IN_TRANSIT
