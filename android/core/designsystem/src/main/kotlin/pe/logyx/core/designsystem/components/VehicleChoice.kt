package pe.logyx.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.VehicleFit
import pe.logyx.core.model.VehicleStatus
import pe.logyx.core.model.VehicleType
import pe.logyx.core.model.VehicleView

/** Un vehículo de la flota con su veredicto de RN02 para una carga concreta (`fitFor`). */
data class VehicleChoice(val vehicle: VehicleView, val fit: VehicleFit)

/**
 * Qué dice cada rechazo de RN02, en español (`CAP-001..004`). [cargoType] = null cuando se desconoce (p. ej. un envío):
 * el motivo de tipo queda genérico.
 */
@Composable
fun VehicleFit.reason(vehicle: VehicleView, weightKg: Double, volumeM3: Double?, cargoType: CargoType? = null): String? = when (this) {
    VehicleFit.FITS -> null
    VehicleFit.UNAVAILABLE -> when (vehicle.status) {
        VehicleStatus.IN_ROUTE -> stringResource(R.string.ds_vehicle_choice_unavailable_route)
        VehicleStatus.MAINTENANCE -> stringResource(R.string.ds_vehicle_choice_unavailable_maintenance)
        VehicleStatus.AVAILABLE -> stringResource(R.string.ds_vehicle_choice_unavailable)
    }
    VehicleFit.WEIGHT -> stringResource(R.string.ds_vehicle_choice_weight, Fmt.tons(vehicle.maxWeightKg), Fmt.tons(weightKg))
    VehicleFit.TYPE ->
        if (cargoType != null) stringResource(R.string.ds_vehicle_choice_type, cargoType.label.lowercase())
        else stringResource(R.string.ds_vehicle_choice_type_generic)
    VehicleFit.VOLUME -> stringResource(
        R.string.ds_vehicle_choice_volume, Fmt.decimal1(vehicle.maxVolumeM3 ?: 0.0), Fmt.decimal1(volumeM3 ?: 0.0),
    )
}

/**
 * Selector de vehículo con RN02 a la vista: los aptos se pueden elegir; los que no, quedan **deshabilitados con su motivo**
 * (kg, tipo de carga, volumen o disponibilidad) en lugar de desaparecer. Primero los aptos. [error] = error de campo del
 * backend (`CAP-001..005`, `SHP-007`) bajo la lista. Si ninguno es apto, avisa con [AlertBanner]; sin flota, estado vacío.
 * [currentId] marca el vehículo actual del envío ("Vehículo actual").
 */
@Composable
fun VehicleChoiceList(
    choices: List<VehicleChoice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    weightKg: Double,
    volumeM3: Double?,
    modifier: Modifier = Modifier,
    cargoType: CargoType? = null,
    error: String? = null,
    currentId: String? = null,
    enabled: Boolean = true,
    /** RN02 por fecha: vehículos aptos que la agenda bloquea para esa carga (id → motivo, p. ej. «Ocupado · libre el 23 nov»). */
    blocked: Map<String, String> = emptyMap(),
    /** Nota informativa por vehículo (id → texto): «Libre en esas fechas», «1 oferta pendiente en esas fechas». */
    hints: Map<String, String> = emptyMap(),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            choices.isEmpty() -> AlertBanner(
                stringResource(R.string.ds_vehicle_choice_empty_text), kind = MessageKind.Warning,
                title = stringResource(R.string.ds_vehicle_choice_empty_title),
            )
            choices.none { it.fit.isFit } -> AlertBanner(
                stringResource(R.string.ds_vehicle_choice_none_text), kind = MessageKind.Warning,
                title = stringResource(R.string.ds_vehicle_choice_none_title),
            )
        }
        choices.sortedByDescending { it.fit.isFit && it.vehicle.id !in blocked }.forEach { (vehicle, fit) ->
            val agendaBlock = blocked[vehicle.id]
            val usable = fit.isFit && agendaBlock == null
            val reason = fit.reason(vehicle, weightKg, volumeM3, cargoType) ?: agendaBlock
            val notApt = stringResource(R.string.ds_vehicle_choice_not_apt)
            RadioCardRow(
                selected = vehicle.id == selectedId && usable,
                onClick = { onSelect(vehicle.id) },
                enabled = enabled && usable,
                modifier = Modifier.semantics { if (!usable) contentDescription = "${vehicle.plate}. $notApt. $reason" },
            ) {
                Icon(
                    if (usable) Icons.Rounded.LocalShipping else Icons.Rounded.Block, null,
                    tint = if (usable) MaterialTheme.colorScheme.primary else LogyxTheme.colors.dangerText,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(vehicle.plate, style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurface)
                        if (vehicle.id == currentId) InfoChip(stringResource(R.string.ds_vehicle_choice_current))
                    }
                    Text(
                        stringResource(R.string.ds_vehicle_choice_option, vehicle.type.label, Fmt.tons(vehicle.maxWeightKg)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reason != null) Text(reason, style = MaterialTheme.typography.labelMedium, color = LogyxTheme.colors.dangerText)
                    else hints[vehicle.id]?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = LogyxTheme.colors.dangerText)
    }
}

@Preview(showBackground = true)
@Composable
private fun VehicleChoicePreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp)) {
            VehicleChoiceList(
                choices = listOf(
                    VehicleChoice(VehicleView("1", "ABC-101", VehicleType.REFRIGERATED, 8000.0, 30.0, VehicleStatus.AVAILABLE), VehicleFit.FITS),
                    VehicleChoice(VehicleView("2", "XYZ-202", VehicleType.TRUCK_SMALL, 3000.0, null, VehicleStatus.AVAILABLE), VehicleFit.TYPE),
                    VehicleChoice(VehicleView("3", "DEF-303", VehicleType.VAN, 1000.0, null, VehicleStatus.IN_ROUTE), VehicleFit.UNAVAILABLE),
                ),
                selectedId = "1", onSelect = {}, weightKg = 2500.0, volumeM3 = 20.0, cargoType = CargoType.PERISHABLE,
            )
        }
    }
}
