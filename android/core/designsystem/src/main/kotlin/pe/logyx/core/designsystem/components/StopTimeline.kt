package pe.logyx.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pe.logyx.core.designsystem.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopView

/** Paradas en vertical con estado y hora. `large` aumenta tipografía para el modo conductor. */
@Composable
fun StopTimeline(stops: List<StopView>, modifier: Modifier = Modifier, large: Boolean = false) {
    val ordered = stops.sortedBy { it.sequence }
    val current = ordered.firstOrNull { it.status != StopStatus.COMPLETED }
    Column(modifier) {
        ordered.forEachIndexed { i, stop ->
            val isLast = i == ordered.lastIndex
            val done = stop.status == StopStatus.COMPLETED
            val isCurrent = stop.id == current?.id
            val node = if (large) 28.dp else 22.dp
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.width(node), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimelineNode(done, isCurrent, node)
                    if (!isLast) {
                        Box(
                            Modifier.width(2.dp).fillMaxHeight()
                                .background(if (done) LogyxTheme.colors.money else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Column(Modifier.padding(start = 12.dp, bottom = if (isLast) 0.dp else 16.dp)) {
                    Text(
                        "${stop.type.label} · ${cityLabel(stop.city)}",
                        style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                        color = if (done || isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stop.address?.let {
                        Text(it, style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val detail = when (stop.status) {
                        StopStatus.COMPLETED -> stringResource(R.string.ds_stop_completed, Fmt.time(stop.completedAt))
                        StopStatus.ARRIVED -> stringResource(R.string.ds_stop_arrived, Fmt.time(stop.arrivedAt))
                        StopStatus.PENDING -> if (isCurrent) stringResource(R.string.ds_stop_next) else stop.status.label
                    }
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) LogyxTheme.colors.onMoneyContainer else if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    // 2.5: peso medido en el recojo, quién recibió y si lo registró la oficina del transportista (con su motivo).
                    stop.loadedWeightKg?.let {
                        Text(stringResource(R.string.ds_stop_loaded_weight, Fmt.kg(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    stop.receivedByName?.takeIf { it.isNotBlank() }?.let { name ->
                        val who = listOfNotNull(name, stop.receivedByDoc?.takeIf { it.isNotBlank() }).joinToString(" · ")
                        Text(stringResource(R.string.ds_stop_received_by, who), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    (stop.completionOfficeReason?.takeIf { it.isNotBlank() } ?: stop.arrivalOfficeReason?.takeIf { it.isNotBlank() })?.let { reason ->
                        LogyxBadge(
                            stringResource(R.string.ds_stop_office, reason), Modifier.padding(top = 4.dp),
                            tone = PillTone.Amber, icon = Icons.Rounded.Apartment,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(done: Boolean, current: Boolean, size: Dp) {
    val money = LogyxTheme.colors.money
    val primary = MaterialTheme.colorScheme.primary
    when {
        done -> Box(Modifier.size(size).background(money, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, Modifier.size(size * 0.65f), tint = androidx.compose.ui.graphics.Color.White)
        }
        current -> Box(Modifier.size(size).border(3.dp, primary, CircleShape).padding(size / 4).background(primary, CircleShape))
        else -> Box(Modifier.size(size).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
    }
}
