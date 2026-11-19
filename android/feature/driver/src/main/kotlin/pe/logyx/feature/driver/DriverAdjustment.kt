package pe.logyx.feature.driver

import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.AdjustmentType
import pe.logyx.core.model.ShipmentView

/** `@Size(max = 300)` de la nota del ajuste. */
const val MAX_ADJUSTMENT_NOTE = 300

/** Tipos que el conductor puede proponer (sin `UNKNOWN`). */
val driverAdjustmentTypes: List<AdjustmentType> = AdjustmentType.entries.filter { it != AdjustmentType.UNKNOWN }

/** Estos tipos se refieren a una parada: la actual se propone por defecto. */
val AdjustmentType.defaultsToCurrentStop: Boolean get() = this == AdjustmentType.DETENTION || this == AdjustmentType.WEIGHT_DIFF

/**
 * Formulario «Proponer ajuste» (roadmap 2.6). [amount] es el texto del `MoneyField` (con IGV, siempre positivo: el conductor
 * no propone descuentos). Los errores se llenan al intentar enviar.
 */
data class AdjustmentForm(
    val type: AdjustmentType? = null,
    val amount: String = "",
    val note: String = "",
    val stopId: String? = null,
    val typeError: UiText? = null,
    val amountError: UiText? = null,
    val noteError: UiText? = null,
) {
    val amountValue: Double? get() = amount.toDoubleOrNull()?.let { Math.round(it * 100) / 100.0 }
}

/** Devuelve el formulario con los errores que tenga (todos en null = se puede enviar). [agreedPrice] = tope si el envío lo trae. */
fun AdjustmentForm.validated(agreedPrice: Double?): AdjustmentForm {
    val value = amountValue
    return copy(
        typeError = if (type == null) UiText.of(R.string.driver_adjustment_type_required) else null,
        amountError = when {
            value == null || value <= 0.0 -> UiText.of(R.string.driver_adjustment_amount_required)
            agreedPrice != null && agreedPrice > 0.0 && value > agreedPrice ->
                UiText.of(R.string.driver_adjustment_amount_too_high, pe.logyx.core.designsystem.Fmt.money(agreedPrice))
            else -> null
        },
        noteError = if (type == AdjustmentType.OTHER && note.isBlank()) UiText.of(R.string.driver_adjustment_note_required) else null,
    )
}

val AdjustmentForm.hasErrors: Boolean get() = typeError != null || amountError != null || noteError != null

/** Precio acordado del envío (con IGV) si el servidor lo trae; es el tope de un ajuste. */
val ShipmentView.adjustmentCap: Double? get() = agreedPrice?.takeIf { it > 0.0 }
