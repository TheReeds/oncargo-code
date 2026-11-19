package pe.logyx.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

// Ola 2, zona X (roadmap 2.5): componentes del conductor sin señal.

/** Estado resumido de la cola: `Queued` en cola, `Sending` enviando, `WaitingSignal` sin red, `Failed` algo necesita atención. */
enum class SyncChipState { Queued, Sending, WaitingSignal, Failed }

/**
 * Indicador compacto «3 acciones por sincronizar» (singular «1 acción…») con su estado; al tocarlo abre el detalle (una
 * `LogyxBottomSheet` con la lista y «Reintentar ahora»). No pinta nada con [count] = 0. Área táctil ≥ 48 dp.
 */
@Composable
fun SyncStatusChip(count: Int, state: SyncChipState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val c = LogyxTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (state) {
        SyncChipState.Failed -> c.dangerContainer to c.dangerText
        SyncChipState.WaitingSignal -> c.warningContainer to c.onWarningContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    val icon = when (state) {
        SyncChipState.Sending -> Icons.Rounded.Sync
        SyncChipState.WaitingSignal -> Icons.Rounded.CloudOff
        SyncChipState.Failed -> Icons.Rounded.ErrorOutline
        SyncChipState.Queued -> Icons.Rounded.CloudUpload
    }
    val title = pluralStringResource(R.plurals.ds_sync_count, count, count)
    val sub = when (state) {
        SyncChipState.Sending -> stringResource(R.string.ds_sync_sending)
        SyncChipState.WaitingSignal -> stringResource(R.string.ds_sync_waiting_signal)
        SyncChipState.Failed -> stringResource(R.string.ds_sync_failed)
        SyncChipState.Queued -> null
    }
    val a11y = stringResource(R.string.ds_sync_a11y, title, sub ?: "")
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = fg)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

/**
 * Número con unidad (kg, m³) para formularios: teclado decimal, solo dígitos y un separador (coma o punto), unidad como
 * sufijo. El estado guarda el texto crudo; conviértelo en el ViewModel (p. ej. `parseWeightKg`). [error] = qué pasó + qué hacer.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    optional: Boolean = false,
    enabled: Boolean = true,
    maxLength: Int = 9,
) {
    LogyxTextField(
        value = value,
        onValueChange = { raw ->
            var seenSeparator = false
            val clean = raw.filter { ch ->
                when {
                    ch.isDigit() -> true
                    (ch == '.' || ch == ',') && !seenSeparator -> { seenSeparator = true; true }
                    else -> false
                }
            }.take(maxLength)
            onValueChange(clean)
        },
        label = label,
        modifier = modifier,
        hint = hint,
        error = error,
        suffix = unit,
        optional = optional,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = LogyxText.mono,
    )
}

@Preview(showBackground = true)
@Composable
private fun SyncStatusChipPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncStatusChip(3, SyncChipState.WaitingSignal, {})
            SyncStatusChip(1, SyncChipState.Sending, {})
            SyncStatusChip(2, SyncChipState.Failed, {})
            SyncStatusChip(1, SyncChipState.Queued, {})
            NumberField("2450", {}, "Peso medido", "kg", hint = "Lo que marcó la balanza.")
            NumberField("0", {}, "Peso medido", "kg", error = "Ingresa un peso entre 1 y 60,000 kg.")
        }
    }
}
