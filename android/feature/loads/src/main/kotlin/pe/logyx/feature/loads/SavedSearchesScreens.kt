package pe.logyx.feature.loads

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxSwitchRow
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.MAX_SAVED_SEARCH_NAME
import pe.logyx.core.model.SavedSearch
import pe.logyx.core.model.VehicleView

/** Acciones de «Mis búsquedas» y «Guardar búsqueda» (las cablea [LoadsFeedRoute] con [SavedSearchesViewModel]). */
data class SavedSearchesActions(
    val onRefresh: () -> Unit = {},
    val onSave: (name: String) -> Unit = {},
    val onSetActive: (SavedSearch, Boolean) -> Unit = { _, _ -> },
    val onAskDelete: (SavedSearch) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onResetSave: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Resumen de una búsqueda con las ciudades del catálogo y los textos de `strings.xml` («Lima → Juliaca · General · Hasta 5.0 t»). */
internal fun SavedSearch.summaryText(context: Context, cities: List<CityItem>, vehicles: List<VehicleView>): String {
    fun city(code: String) = cities.firstOrNull { it.code == code }?.name ?: cityLabel(code)
    return summaryParts(
        city = ::city,
        cargo = { context.getString(cargoLabelRes(it)) },
        weight = { context.getString(R.string.loads_saved_weight_up_to, Fmt.tons(it)) },
        dates = { from, to ->
            when {
                from != null && to != null -> context.getString(R.string.loads_saved_dates_range, Fmt.date(from), Fmt.date(to))
                from != null -> context.getString(R.string.loads_saved_dates_from, Fmt.date(from))
                else -> context.getString(R.string.loads_saved_dates_until, Fmt.date(to))
            }
        },
        vehicle = { id -> vehicles.firstOrNull { it.id == id }?.plate?.let { context.getString(R.string.loads_saved_vehicle, it) } },
        anyCity = context.getString(R.string.loads_saved_any_city),
    ).joinToString(" · ")
}

private fun cargoLabelRes(type: CargoType): Int = when (type) {
    CargoType.GENERAL -> pe.logyx.core.designsystem.R.string.ds_cargotype_general
    CargoType.FRAGILE -> pe.logyx.core.designsystem.R.string.ds_cargotype_fragile
    CargoType.PERISHABLE -> pe.logyx.core.designsystem.R.string.ds_cargotype_perishable
    CargoType.HAZARDOUS -> pe.logyx.core.designsystem.R.string.ds_cargotype_hazardous
    CargoType.BULK -> pe.logyx.core.designsystem.R.string.ds_cargotype_bulk
    CargoType.OVERSIZED -> pe.logyx.core.designsystem.R.string.ds_cargotype_oversized
}

/**
 * «Mis búsquedas»: lista con nombre + resumen del filtro, interruptor para pausar/reanudar y borrar con confirmación.
 * Escribir (guardar, pausar, borrar) solo con `OPERATE` ([SavedSearchesUiState.canWrite]); el Lector solo mira.
 * [canSaveCurrent] = los filtros de la pantalla de cargas se pueden guardar (hay al menos uno).
 */
@Composable
fun SavedSearchesSheet(
    state: SavedSearchesUiState,
    cities: List<CityItem>,
    vehicles: List<VehicleView>,
    canSaveCurrent: Boolean,
    onSaveCurrent: () -> Unit,
    actions: SavedSearchesActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.loads_saved_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.loads_saved_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Los avisos de la pantalla quedan tapados por la hoja: el resultado de pausar/reanudar/borrar se ve aquí.
            state.message?.let { m ->
                AlertBanner(m.resolve(context), kind = m.kind, onDismiss = actions.onMessageShown)
            }
            if (canSaveCurrent && state.canWrite) {
                LogyxButton(
                    stringResource(R.string.loads_saved_save_current), onSaveCurrent,
                    variant = ButtonVariant.Secondary, size = ButtonSize.Medium, requiresOperate = true,
                )
            }
            when {
                state.loading -> SkeletonList(count = 2)
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, actions.onRefresh)
                state.items.isEmpty() -> EmptyState(
                    stringResource(R.string.loads_saved_empty_title), stringResource(R.string.loads_saved_empty_message),
                )
                else -> state.items.forEach { item ->
                    SavedSearchCard(item, item.summaryText(context, cities, vehicles), state, actions)
                }
            }
        }
    }
    state.deleting?.let { item ->
        LogyxConfirmDialog(
            title = stringResource(R.string.loads_saved_delete_title),
            message = stringResource(R.string.loads_saved_delete_message, item.name),
            confirmLabel = stringResource(R.string.loads_saved_delete_confirm),
            onConfirm = actions.onConfirmDelete, onDismiss = actions.onDismissDelete,
            dismissLabel = stringResource(R.string.loads_saved_delete_keep), destructive = true, loading = state.deleteBusy,
        )
    }
}

@Composable
private fun SavedSearchCard(item: SavedSearch, summary: String, state: SavedSearchesUiState, actions: SavedSearchesActions) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary.ifEmpty { stringResource(R.string.loads_saved_no_summary) },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.canWrite) {
                IconButton(onClick = { actions.onAskDelete(item) }) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.loads_saved_delete_description, item.name))
                }
            }
        }
        LogyxSwitchRow(
            label = stringResource(if (item.active) R.string.loads_saved_active else R.string.loads_saved_paused_label),
            checked = item.active, onCheckedChange = { actions.onSetActive(item, it) },
            enabled = state.canWrite && item.id !in state.busyIds,
        )
    }
}

/** Hoja con el nombre para guardar los filtros actuales. Los errores de la API (`ALT-001/002/003`) salen bajo el campo, tal cual. */
@Composable
fun SaveSearchSheet(
    initialName: String,
    summary: String,
    state: SavedSearchesUiState,
    actions: SavedSearchesActions,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    LaunchedEffect(state.saved) { if (state.saved) onDismiss() }
    LogyxBottomSheet(onDismiss = { if (!state.saving) onDismiss() }, title = stringResource(R.string.loads_saved_save_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LogyxTextField(
                value = name, onValueChange = { name = it.take(MAX_SAVED_SEARCH_NAME); if (state.saveError != null) actions.onResetSave() },
                label = stringResource(R.string.loads_saved_name_label), error = state.saveError?.asString(),
                hint = stringResource(R.string.loads_saved_name_hint), enabled = !state.saving,
            )
            LogyxButton(
                stringResource(R.string.loads_saved_save_action), { actions.onSave(name) },
                variant = ButtonVariant.Primary, loading = state.saving, enabled = name.isNotBlank(), requiresOperate = true,
            )
            LogyxButton(stringResource(R.string.loads_saved_back), onDismiss, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = !state.saving)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SavedSearchesPreview() {
    LogyxTheme {
        val items = listOf(
            SavedSearch("1", "Lima a Juliaca", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, maxWeightKg = 5000.0),
            SavedSearch("2", "Perecibles", cargoType = CargoType.PERISHABLE, active = false),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val st = SavedSearchesUiState(loading = false, items = items)
            items.forEach { SavedSearchCard(it, it.name, st, SavedSearchesActions()) }
        }
    }
}
