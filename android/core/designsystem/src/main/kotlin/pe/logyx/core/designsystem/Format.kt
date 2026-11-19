package pe.logyx.core.designsystem

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

/** Formatos del producto: montos `S/ 1,850.00`, fechas `28 nov · 08:00` (hora de Lima, UTC-5). */
object Fmt {
    private val lima = ZoneOffset.ofHours(-5)
    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    private fun decimal(pattern: String) =
        DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))

    fun money(amount: Double?): String = amount?.let { "S/ " + decimal("#,##0.00").format(it) } ?: "—"

    /** Monto sin decimales cuando es entero, útil en chips: `S/ 1,850`. */
    fun moneyShort(amount: Double?): String = amount?.let { "S/ " + decimal("#,##0").format(it) } ?: "—"

    fun decimal1(value: Double): String = decimal("0.0").format(value)

    /** Peso en kg con miles y hasta 1 decimal: `3,200 kg`, `2,450.5 kg`. */
    fun kg(value: Double?): String = value?.let { decimal("#,##0.#").format(it) + " kg" } ?: "—"

    fun km(value: Double?): String = value?.let { decimal("#,##0").format(it) + " km" } ?: "—"

    fun tons(weightKg: Double?): String = weightKg?.let {
        decimal("0.0").format(it / 1000.0) + " t"
    } ?: "—"

    /** `28 nov · 08:00` desde un instante ISO-8601 con zona. */
    fun dateTime(iso: String?): String {
        val d = parse(iso) ?: return "—"
        return "${d.dayOfMonth} ${months[d.monthValue - 1]} · %02d:%02d".format(d.hour, d.minute)
    }

    /** `28 nov` desde una fecha `yyyy-MM-dd` (o un instante ISO). */
    fun date(iso: String?): String {
        if (iso == null) return "—"
        runCatching { LocalDate.parse(iso) }.getOrNull()?.let {
            return "${it.dayOfMonth} ${months[it.monthValue - 1]}"
        }
        val d = parse(iso) ?: return "—"
        return "${d.dayOfMonth} ${months[d.monthValue - 1]}"
    }

    fun time(iso: String?): String {
        val d = parse(iso) ?: return "—"
        return "%02d:%02d".format(d.hour, d.minute)
    }

    fun parseInstant(iso: String?): Instant? = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    private fun parse(iso: String?) = parseInstant(iso)?.atOffset(lima)

    /** Cuenta regresiva: `12 min`, `45 s`, `2 h 05 min`. Vacío si ya venció. */
    fun remaining(closesAtIso: String?, now: Instant = Instant.now()): String? {
        val end = parseInstant(closesAtIso) ?: return null
        val secs = end.epochSecond - now.epochSecond
        if (secs <= 0) return null
        return when {
            secs < 60 -> "$secs s"
            secs < 3600 -> "${(secs + 59) / 60} min"
            secs < 86_400 -> "${secs / 3600} h %02d min".format((secs % 3600) / 60)
            else -> "${secs / 86_400} d ${(secs % 86_400) / 3600} h"
        }
    }

    /** Segundos restantes (para decidir el color ámbar < 5 min). */
    fun remainingSeconds(closesAtIso: String?, now: Instant = Instant.now()): Long? =
        parseInstant(closesAtIso)?.let { it.epochSecond - now.epochSecond }
}
