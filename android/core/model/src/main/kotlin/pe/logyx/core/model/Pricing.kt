package pe.logyx.core.model

import kotlinx.serialization.Serializable

/**
 * Comisión de OnCargo e IGV sobre el precio adjudicado (ADR-0010, decisión del dueño 2026-11-18). Espejo de
 * `pricing/domain/CommissionPolicy` del backend, con aritmética en céntimos para redondear igual (HALF_UP):
 *
 * - valor de venta = precio / 1.18 · IGV = precio − valor de venta
 * - comisión = valor de venta × tasa (sin IGV) · IGV de la comisión = comisión × 0.18
 * - neto del transportista = precio − comisión − IGV de la comisión
 *
 * Ejemplo: S/ 1,180 al 8 %: valor de venta 1,000.00 · IGV 180.00 · comisión 80.00 · IGV de la comisión 14.40 · neto 1,085.60.
 * La fuente de verdad sigue siendo la API (`GET /pricing/commission?amount=`, `ShipmentView.priceBreakdown`).
 */
data class PriceBreakdown(
    /** Precio adjudicado con IGV. */
    val amount: Double,
    val netOfVat: Double,
    val vat: Double,
    val commissionRate: Double,
    /** Comisión sin IGV (sobre el valor de venta). */
    val commission: Double,
    val commissionVat: Double,
    /** Lo que cobra el transportista: «cobras S/ X neto». */
    val carrierNet: Double,
)

private fun halfUpDiv(n: Long, d: Long): Long = Math.floorDiv(2 * n + d, 2 * d)

/** Desglose de [amount] (soles, IGV incluido) con la tasa de comisión [commissionRate] (0–1). */
fun priceBreakdown(amount: Double, commissionRate: Double): PriceBreakdown {
    val cents = Math.round(amount * 100).coerceAtLeast(0)
    val netCents = halfUpDiv(cents * 100, 118)
    val vatCents = cents - netCents
    val rateBp = Math.round(commissionRate * 10_000)
    val commissionCents = halfUpDiv(netCents * rateBp, 10_000)
    val commissionVatCents = halfUpDiv(commissionCents * 18, 100)
    return PriceBreakdown(
        amount = cents / 100.0, netOfVat = netCents / 100.0, vat = vatCents / 100.0, commissionRate = commissionRate,
        commission = commissionCents / 100.0, commissionVat = commissionVatCents / 100.0,
        carrierNet = (cents - commissionCents - commissionVatCents) / 100.0,
    )
}

/** Atajos de monto de la oferta rápida. RN03: ninguno baja del piso. RN04: no hay «mejor oferta − paso» (los montos ajenos son secretos). */
enum class BidShortcut { SUGGESTED, MINUS_5, MINUS_10, FLOOR }

/** Monto del atajo en soles, nunca por debajo del piso (RN03); nulo si falta el dato de la carga. */
fun BidShortcut.amount(suggested: Double?, floor: Double?): Double? {
    val fl = floor ?: 0.0
    if (this == BidShortcut.FLOOR) return floor
    if (suggested == null || suggested <= 0) return null
    val raw = when (this) {
        BidShortcut.SUGGESTED -> suggested
        BidShortcut.MINUS_5 -> Math.round(suggested * 0.95).toDouble()
        BidShortcut.MINUS_10 -> Math.round(suggested * 0.90).toDouble()
        BidShortcut.FLOOR -> fl
    }
    return maxOf(fl, raw)
}

/** Respuesta de `GET /pricing/commission`: la tasa vigente que OnCargo retiene al transportista (0–1) y el IGV. */
@Serializable
data class CommissionInfo(val commissionRate: Double, val vatRate: Double = 0.18)

/** Aviso que acompaña a una oferta aceptada (`LOGYX-CAP-006`: el vehículo ya tiene ofertas para esas fechas). */
@Serializable
data class BidWarning(val code: String? = null, val message: String? = null)

/** Un compromiso de la agenda de un vehículo: `SHIPMENT` (envío activo, bloquea) u `BID` (oferta pendiente, solo avisa). */
@Serializable
data class BusySlot(val kind: String? = null, val label: String? = null, val from: String? = null, val to: String? = null)

/**
 * RN02 por fecha de recojo: elegibilidad de un vehículo para ofertar a una carga (`GET /shipment-requests/{id}/vehicle-eligibility`).
 * `eligible = false`: `blockerCode`/`blockerMessage` dicen por qué (`CAP-005` envío activo solapado, `CAP-007` demasiadas ofertas);
 * `freeFrom` = desde cuándo queda libre por envíos activos.
 */
@Serializable
data class VehicleEligibility(
    val vehicleId: String,
    val plate: String? = null,
    val eligible: Boolean = true,
    val blockerCode: String? = null,
    val blockerMessage: String? = null,
    val freeFrom: String? = null,
    val busy: List<BusySlot> = emptyList(),
    val warnings: List<BidWarning> = emptyList(),
)

@Serializable
data class FleetEligibility(
    val windowFrom: String? = null,
    val windowTo: String? = null,
    val maxOverlappingBids: Int = 3,
    val vehicles: List<VehicleEligibility> = emptyList(),
)
