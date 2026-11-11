package pe.logyx.core.designsystem.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale del producto: español de Perú. */
val PeLocale: Locale = Locale.Builder().setLanguage("es").setRegion("PE").build()

/** Aplica es-PE (textos y formato de fechas del calendario) a los selectores de Material 3. */
@Composable
internal fun WithPeLocale(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val cfg = LocalConfiguration.current
    val (ctx, conf) = remember(base, cfg) {
        Locale.setDefault(PeLocale)
        val c = Configuration(cfg).apply { setLocale(PeLocale) }
        val localized: Context = base.createConfigurationContext(c)
        localized to c
    }
    CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides conf) { content() }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val DisplayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", PeLocale)

/** `28 nov 2026` para mostrar dentro del campo. */
fun formatPickerDate(date: LocalDate): String = DisplayFormat.format(date).replace(".", "")

@OptIn(ExperimentalMaterial3Api::class)
private fun selectableFrom(min: LocalDate?, max: LocalDate?) = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val d = utcTimeMillis.toLocalDate()
        return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
    }
    override fun isSelectableYear(year: Int) =
        (min == null || year >= min.year) && (max == null || year <= max.year)
}

/** Diálogo de fecha única, en es-PE, con atajos "Hoy" y "Mañana". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogyxDatePicker(
    selected: LocalDate?,
    onSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = LocalDate.now(),
    maxDate: LocalDate? = null,
) {
    WithPeLocale {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selected?.toUtcMillis(),
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = selectableFrom(minDate, maxDate),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                LogyxButton(
                    stringResource(R.string.ds_accept),
                    { state.selectedDateMillis?.let { onSelected(it.toLocalDate()) }; onDismiss() },
                    size = ButtonSize.Medium, fullWidth = false, enabled = state.selectedDateMillis != null,
                )
            },
            dismissButton = {
                LogyxButton(stringResource(R.string.ds_cancel), onDismiss, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false)
            },
        ) {
            Column {
                ChipRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val today = LocalDate.now()
                    LogyxFilterChip(stringResource(R.string.ds_today), state.selectedDateMillis == today.toUtcMillis(), { state.selectedDateMillis = today.toUtcMillis() })
                    val tomorrow = today.plusDays(1)
                    LogyxFilterChip(stringResource(R.string.ds_tomorrow), state.selectedDateMillis == tomorrow.toUtcMillis(), { state.selectedDateMillis = tomorrow.toUtcMillis() })
                }
                DatePicker(state, showModeToggle = false, colors = pickerColors())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pickerColors() = DatePickerDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surface,
    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
    todayDateBorderColor = MaterialTheme.colorScheme.primary,
    todayContentColor = MaterialTheme.colorScheme.primary,
    dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * Selector de rango en una hoja inferior, en es-PE, con atajos "Hoy" y "Próximos 7 días".
 * [onConfirm] recibe inicio y fin (el fin es igual al inicio si solo se eligió un día).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogyxDateRangePicker(
    start: LocalDate?,
    end: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = LocalDate.now(),
    maxDate: LocalDate? = null,
) {
    WithPeLocale {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.toUtcMillis(),
            initialSelectedEndDateMillis = end?.toUtcMillis(),
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = selectableFrom(minDate, maxDate),
        )
        LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ds_pick_date_range)) {
            ChipRow(Modifier.padding(horizontal = 20.dp)) {
                val today = LocalDate.now()
                val s = state.selectedStartDateMillis
                val e = state.selectedEndDateMillis
                LogyxFilterChip(
                    stringResource(R.string.ds_today),
                    s == today.toUtcMillis() && (e == null || e == s),
                    { state.setSelection(today.toUtcMillis(), today.toUtcMillis()) },
                )
                LogyxFilterChip(
                    stringResource(R.string.ds_next_7_days),
                    s == today.toUtcMillis() && e == today.plusDays(6).toUtcMillis(),
                    { state.setSelection(today.toUtcMillis(), today.plusDays(6).toUtcMillis()) },
                )
            }
            DateRangePicker(
                state,
                Modifier.height(480.dp),
                showModeToggle = false,
                title = null,
                headline = null,
                colors = pickerColors(),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogyxButton(stringResource(R.string.ds_cancel), onDismiss, Modifier.weight(1f), variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
                LogyxButton(
                    stringResource(R.string.ds_apply),
                    {
                        val s = state.selectedStartDateMillis
                        if (s != null) onConfirm(s.toLocalDate(), (state.selectedEndDateMillis ?: s).toLocalDate())
                        onDismiss()
                    },
                    Modifier.weight(1f), size = ButtonSize.Medium,
                    enabled = state.selectedStartDateMillis != null,
                )
            }
        }
    }
}

/** Campo de fecha que abre [LogyxDatePicker]. */
@Composable
fun LogyxDateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    placeholder: String = stringResource(R.string.ds_pick_date),
    minDate: LocalDate? = LocalDate.now(),
    maxDate: LocalDate? = null,
    optional: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    LogyxPickerField(
        label, value?.let(::formatPickerDate), { open = true }, modifier,
        placeholder = placeholder, hint = hint, error = error, optional = optional,
        leadingIcon = Icons.Rounded.CalendarMonth, trailingIcon = null,
    )
    if (open) LogyxDatePicker(value, onValueChange, { open = false }, minDate, maxDate)
}

/** Campo de rango de fechas que abre [LogyxDateRangePicker]. */
@Composable
fun LogyxDateRangeField(
    label: String,
    start: LocalDate?,
    end: LocalDate?,
    onValueChange: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    placeholder: String = stringResource(R.string.ds_pick_date_range),
    minDate: LocalDate? = LocalDate.now(),
    maxDate: LocalDate? = null,
    optional: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val text = when {
        start == null -> null
        end == null || end == start -> formatPickerDate(start)
        else -> "${formatPickerDate(start)} – ${formatPickerDate(end)}"
    }
    LogyxPickerField(
        label, text, { open = true }, modifier,
        placeholder = placeholder, hint = hint, error = error, optional = optional,
        leadingIcon = Icons.Rounded.DateRange, trailingIcon = null,
    )
    if (open) LogyxDateRangePicker(start, end, onValueChange, { open = false }, minDate, maxDate)
}

/** Diálogo de hora (formato 24 h) para la salida de un viaje. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogyxTimePicker(
    selected: LocalTime?,
    onSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    WithPeLocale {
        val state = rememberTimePickerState(initialHour = selected?.hour ?: 8, initialMinute = selected?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            text = { TimePicker(state) },
            confirmButton = {
                LogyxButton(
                    stringResource(R.string.ds_accept), { onSelected(LocalTime.of(state.hour, state.minute)); onDismiss() },
                    size = ButtonSize.Medium, fullWidth = false,
                )
            },
            dismissButton = {
                LogyxButton(stringResource(R.string.ds_cancel), onDismiss, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false)
            },
        )
    }
}

/** `08:00` para mostrar la hora elegida dentro del campo. */
fun formatPickerTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/** Campo de hora que abre [LogyxTimePicker] (24 h). Úsalo junto a [LogyxDateField] para fecha-hora. */
@Composable
fun LogyxTimeField(
    label: String,
    value: LocalTime?,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    placeholder: String = stringResource(R.string.ds_pick_time),
    optional: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    LogyxPickerField(
        label, value?.let(::formatPickerTime), { open = true }, modifier,
        placeholder = placeholder, hint = hint, error = error, optional = optional,
        leadingIcon = Icons.Rounded.Schedule, trailingIcon = null,
    )
    if (open) LogyxTimePicker(value, onValueChange, { open = false })
}

@Preview(showBackground = true)
@Composable
private fun DateFieldsPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LogyxDateField("Fecha de recojo", LocalDate.of(2026, 11, 28), {})
            LogyxDateRangeField("Disponibilidad", LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 3), { _, _ -> })
            LogyxTimeField("Hora de salida", LocalTime.of(8, 0), {})
        }
    }
}
