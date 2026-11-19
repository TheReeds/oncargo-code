package pe.logyx.feature.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.AdjustmentType
import pe.logyx.core.model.StopView

/** Intenciones de «Proponer ajuste» (la pantalla no conoce el ViewModel). */
class AdjustmentActions(
    val onOpen: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onType: (AdjustmentType) -> Unit = {},
    val onAmount: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onStop: (String?) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/**
 * Hoja «Proponer ajuste»: tipo, monto con IGV, nota (obligatoria en «Otro») y parada. «Enviar ajuste» pide confirmar con el
 * monto antes de encolar.
 */
@Composable
internal fun AdjustmentSheet(
    form: AdjustmentForm,
    stops: List<StopView>,
    confirming: Boolean,
    actions: AdjustmentActions,
) {
    LogyxBottomSheet(onDismiss = actions.onClose, title = stringResource(R.string.driver_adjustment_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.driver_adjustment_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val typeLabels = driverAdjustmentTypes.associateWith { it.label }
            LogyxDropdown(
                label = stringResource(R.string.driver_adjustment_type),
                options = driverAdjustmentTypes,
                selected = form.type,
                onSelected = actions.onType,
                optionLabel = { typeLabels[it].orEmpty() },
                error = form.typeError?.asString(),
            )
            MoneyField(
                form.amount, actions.onAmount, stringResource(R.string.driver_adjustment_amount),
                hint = stringResource(R.string.driver_adjustment_amount_hint), error = form.amountError?.asString(),
            )
            val labels = stops.associate {
                it.id to stringResource(R.string.driver_adjustment_stop_option, it.sequence, cityLabel(it.city), it.type.label)
            }
            LogyxDropdown(
                label = stringResource(R.string.driver_adjustment_stop),
                options = stops.map { it.id },
                selected = form.stopId,
                onSelected = { actions.onStop(it) },
                optionLabel = { labels[it].orEmpty() },
                placeholder = stringResource(R.string.driver_adjustment_stop_none),
                optional = true,
                onClear = { actions.onStop(null) },
            )
            LogyxTextField(
                form.note, actions.onNote, stringResource(R.string.driver_adjustment_note),
                placeholder = stringResource(R.string.driver_adjustment_note_placeholder),
                error = form.noteError?.asString(), optional = form.type != AdjustmentType.OTHER,
                singleLine = false, minLines = 2, maxLines = 4,
                hint = "${form.note.length}/$MAX_ADJUSTMENT_NOTE",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            LogyxButton(stringResource(R.string.driver_adjustment_submit), actions.onSubmit, size = ButtonSize.Large)
        }
    }
    val amount = form.amountValue
    if (confirming && amount != null) {
        LogyxConfirmDialog(
            title = stringResource(R.string.driver_adjustment_confirm_title, Fmt.money(amount)),
            message = stringResource(R.string.driver_adjustment_confirm_message),
            confirmLabel = stringResource(R.string.driver_adjustment_confirm_button),
            dismissLabel = stringResource(R.string.driver_adjustment_back),
            onConfirm = actions.onConfirm,
            onDismiss = actions.onCancelConfirm,
        )
    }
}
