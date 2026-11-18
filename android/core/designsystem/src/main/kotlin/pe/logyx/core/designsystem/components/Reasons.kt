package pe.logyx.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/** Largo máximo de los motivos de cancelación y relevo (`@Size(max = 500)` del backend). */
const val MAX_REASON_LENGTH = 500

/**
 * Hoja con un motivo en texto libre: cancelar solicitud o envío, cambiar vehículo, relevar al conductor.
 * Es el patrón de "acción con motivo" de OnCargo: título = pregunta, [message] = consecuencia, campo "Motivo"
 * (obligatorio u [optional]) con contador y el aviso de RN07, y un botón de verbo + objeto.
 *
 * - [error] es el error del campo (`NEG-001` de RN07, `SHP-006` motivo obligatorio…): se pinta bajo el campo.
 * - [confirmEnabled] deja bloquear el botón mientras falte algo (p. ej. elegir un vehículo en [content]).
 * - [content] va entre el mensaje y el motivo (p. ej. el selector de vehículo o de conductor).
 * El botón lleva `requiresOperate`: con el rol Lector queda deshabilitado.
 */
@Composable
fun LogyxReasonSheet(
    title: String,
    reasonLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    error: String? = null,
    hint: String? = null,
    optional: Boolean = false,
    destructive: Boolean = true,
    loading: Boolean = false,
    confirmEnabled: Boolean = true,
    maxLength: Int = MAX_REASON_LENGTH,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    LogyxBottomSheet(onDismiss = { if (!loading) onDismiss() }, modifier = modifier, title = title) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
            LogyxTextField(
                value = value,
                onValueChange = { onValueChange(it.take(maxLength)) },
                label = reasonLabel,
                error = error,
                hint = hint ?: (stringResource(R.string.ds_reason_no_contact) + " " + stringResource(R.string.ds_reason_counter, value.length, maxLength)),
                optional = optional,
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                enabled = !loading,
            )
            LogyxButton(
                confirmLabel, onConfirm,
                variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
                loading = loading, enabled = confirmEnabled, requiresOperate = true,
            )
            LogyxButton(stringResource(R.string.ds_reason_back), onDismiss, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = !loading)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReasonSheetPreview() {
    LogyxTheme {
        var text by remember { mutableStateOf("Mi camión se averió") }
        Column(Modifier.padding(16.dp)) {
            LogyxReasonSheet(
                title = "¿Cancelar el envío?", reasonLabel = "Motivo", value = text, onValueChange = { text = it },
                confirmLabel = "Cancelar envío", onConfirm = {}, onDismiss = {},
                message = "La otra parte recibirá el motivo y la solicitud quedará cancelada.",
            )
        }
    }
}
