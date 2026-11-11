package pe.logyx.feature.trips

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** `12,000 kg` (separador de miles es-PE/US, sin decimales salvo que los haya). */
internal fun kgText(value: Double?): String {
    if (value == null) return "—"
    val pattern = if (value % 1.0 == 0.0) "#,##0" else "#,##0.##"
    return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value) + " kg"
}

/** Acepta `2500`, `2,500` y `2500.5`; null si no es un número. */
internal fun parseKg(text: String): Double? = text.replace(",", "").trim().toDoubleOrNull()

/** Deja solo dígitos y un punto decimal mientras se escribe. */
internal fun sanitizeKgInput(text: String): String {
    val cleaned = text.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned.take(7) else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "").take(2)
}

internal const val MAX_TRIP_KG = 40_000.0
internal const val MAX_RATE = 1_000_000.0
